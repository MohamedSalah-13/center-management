package com.codejava.center.service;

import com.codejava.center.core.backup.OffsiteBackup;
import com.codejava.center.core.net.OutboundUrl;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

/**
 * يرفع النسخة المكتوبة إلى مستودعٍ خارج هذا الجهاز.
 *
 * <p>الملفُّ مشفَّرٌ قبل أن يصل هنا - {@code BackupCrypto} يمرّره على الشيفرة قبل أن
 * يمسّ القرص - فما يخرج إلى الشبكة لا يُقرأ عند الوجهة ولا في الطريق. وهذا ما يجعل
 * "مستودعاً عند مزوّد" جواباً مقبولاً أصلاً.</p>
 *
 * <h2>ولا يرمي أبداً</h2>
 *
 * <p>النسخةُ نجحت قبل أن يُستدعى: الملفُّ مكتوبٌ على القرص وقديمُه محذوف. ورميُ
 * استثناءٍ هنا يقلب نسخةً ناجحة إلى "فشلت النسخة الاحتياطية" على الشاشة وفي التنبيه،
 * فيبحث صاحبُ السنتر عن ملفٍّ موجودٍ عنده. نفسُ قاعدة {@code prune} و
 * {@code AlertEngine.raise}: ما كان تالياً لا يُبطل ما قبله.</p>
 *
 * <p><b>ولا يسكت أيضاً.</b> الفشل يعود في {@link OffsiteCopy} فيُكتب في سطر سجلّ
 * المراقبة، ويُعرض على الشاشة، ويُطلق {@code BACKUP_NOT_OFFSITE}. سكوتُه يترك سنتراً
 * يظنّ نسخَه في مأمنٍ خارج الخادم وهي لم تبرحه - وهو أسوأ من ألا يُضبط الرفع أصلاً،
 * لأن الظنّ يمنع السؤال.</p>
 *
 * <h2>والعنوان يُفحص عند كل رفع</h2>
 *
 * <p>بـ{@link OutboundUrl}، ولنفس سبب فحصه في بوابة الرسائل: عنوانٌ يكتبه إنسان
 * ويفتحه البرنامج <b>من داخل الشبكة</b> حاملاً {@code Bearer} هو SSRF بعينه -
 * و{@code 169.254.169.254} أولُ ما يُجرَّب. والفحصُ على العنوان النهائي بمجلَّده واسم
 * ملفه لا على الجذر وحده: إعادةُ توجيهٍ في مقطعٍ مضاف تُبطل فحصَ الجذر.</p>
 */
@Component
@RequiredArgsConstructor
public class OffsiteBackupSender {

    private static final Logger log = LoggerFactory.getLogger(OffsiteBackupSender.class);

    /**
     * مهلةُ الرفع كاملاً.
     *
     * <p>سخيّةٌ لأن نسخةَ سنترٍ قد تبلغ مئات الميغابايت على خطٍّ بطيء، ومحدودةٌ لأن
     * رفعاً معلَّقاً يحتجز خيطَ الجدولة - وهو نفسُ حدّ {@code BackupService} على
     * الأدوات الخارجية.</p>
     */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(30);

    /** مهلةُ فتح الاتصال وحدها: وجهةٌ لا تردّ يجب أن تُعرف بسرعة لا بعد نصف ساعة */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    /** آخر ما يُعرض من ردّ الوجهة عند الرفض؛ الردُّ قد يكون صفحةً كاملة */
    private static final int REASON_TAIL_CHARS = 300;

    private final OffsiteBackup destination;

    /**
     * يرفع الملف، ويعيد ما جرى.
     *
     * @param file النسخة كما كُتبت على القرص - مشفَّرةً إن كان التشفير مفعَّلاً
     */
    public OffsiteCopy send(Path file) {
        String endpoint = destination.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return OffsiteCopy.notRequested();
        }

        char[] token = destination.token();
        try {
            // عنوانٌ كُتب ورمزٌ لم يُضبط: مضبوطٌ ومكسور لا "غير مطلوب". السكوت عنه
            // يترك من ضبط العنوان يظنّ نسخَه خارج الخادم وهي لم تبرحه
            if (token == null || token.length == 0) {
                return OffsiteCopy.failed(I18n.get("error.offsite.tokenMissing"));
            }
            return upload(file, endpoint, token);
        } catch (RuntimeException e) {
            // لا يُرمى: النسخةُ نفسها نجحت، وقلبُها إلى فشلٍ يُرسل صاحبها يبحث عن ملفٍ عنده
            log.error("تعذّر رفع النسخة الاحتياطية إلى المستودع الخارجي: {}", e.getMessage(), e);
            return OffsiteCopy.failed(reasonOf(e));
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
        }
    }

    private OffsiteCopy upload(Path file, String endpoint, char[] token) {
        String name = file.getFileName().toString();
        URI target;
        try {
            target = OutboundUrl.of(join(endpoint, destination.folder(), name)).value();
        } catch (RuntimeException refused) {
            return OffsiteCopy.failed(I18n.format("error.offsite.urlNotAllowed", refused.getMessage()));
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // لا اتّباعَ لإعادة التوجيه: الوجهةُ الأولى وحدها هي التي فُحصت،
                // وردٌّ بـ 302 إلى عنوانٍ داخلي يحمل الرمزَ إليه
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {

            // بناءُ الطلب داخل الحارس: ofFile يفتح الملفَ الآن، وقرصٌ نُزع بين كتابة
            // النسخة ورفعها يصل من هنا لا من الإرسال
            HttpRequest request = HttpRequest.newBuilder(target)
                    .header("Authorization", "Bearer " + new String(token))
                    .header("Content-Type", "application/octet-stream")
                    .timeout(UPLOAD_TIMEOUT)
                    // ofFile يقرأ الملف على دفعات: نسخةٌ بحجم غيغابايت لا تُحمَّل في الذاكرة
                    .PUT(HttpRequest.BodyPublishers.ofFile(file))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            return interpret(response.statusCode(), response.body(), target);

        } catch (IOException e) {
            return OffsiteCopy.failed(I18n.format("error.offsite.requestFailed", reasonOf(e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OffsiteCopy.failed(I18n.format("error.offsite.requestFailed", reasonOf(e)));
        }
    }

    /**
     * ماذا يعني ردُّ المستودع.
     *
     * <p>{@code package-private} ليُفحص مباشرةً، ولسببٍ يستحقّ الذكر: مسارُ الشبكة
     * نفسه <b>لا يمكن أن يُقاد من اختبار</b> - {@link OutboundUrl} يرفض
     * {@code http} ويرفض كلّ عنوانٍ محلي، وخادمٌ محلي هو الاثنان معاً. وذلك هو الحارس
     * يعمل لا نقصاً فيه، فيبقى ما يستحقّ الفحص هذا القرار: أيُّ ردٍّ يعني أن النسخة
     * صارت هناك، وأيُّه يعني أنها لم تصل - والفرقُ بينهما هو الفرقُ بين "في مأمن"
     * و"نظنّها في مأمن".</p>
     *
     * <p>وكلُّ 2xx نجاح لا {@code 200} وحدها: المستودعات تردّ {@code 201} على إنشاء
     * و{@code 204} على كتابةٍ بلا محتوى، ورفضُهما يقلب رفعاً ناجحاً إلى تنبيهٍ كاذب
     * كلَّ ليلة.</p>
     */
    static OffsiteCopy interpret(int status, String body, URI target) {
        if (status / 100 != 2) {
            return OffsiteCopy.failed(I18n.format("error.offsite.rejected", status, tail(body)));
        }
        return OffsiteCopy.stored(target.toString());
    }

    /**
     * يركّب العنوان النهائي من الجذر والمجلَّد واسم الملف.
     *
     * <p>الشرطاتُ تُنظَّف على الجانبين: جذرٌ ينتهي بشرطة ومجلَّدٌ يبدأ بها يصنعان
     * {@code //} في المسار، وهي عند بعض المستودعات مجلَّدٌ فارغ باسمٍ فارغ.</p>
     */
    static String join(String endpoint, String folder, String name) {
        StringBuilder url = new StringBuilder(trimSlashes(endpoint));
        if (folder != null && !folder.isBlank()) {
            url.append('/').append(trimSlashes(folder));
        }
        return url.append('/').append(trimSlashes(name)).toString();
    }

    private static String trimSlashes(String part) {
        String trimmed = part.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String tail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= REASON_TAIL_CHARS
                ? trimmed : trimmed.substring(0, REASON_TAIL_CHARS);
    }
}
