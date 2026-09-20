package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * إرسال مباشر عبر واتساب الرسمي من Meta (WhatsApp Business Cloud API).
 *
 * <p>لا يفتح نافذة ولا ينتظر ضغطة: الدفعة كلها تُرسل من الخادم. الثمن حساب أعمال
 * مسجَّل وتكلفة لكل محادثة، ولهذا يبقى الرابط هو الافتراضي.</p>
 *
 * <p><b>نافذة الأربع والعشرين ساعة — وهي سبب حقل "اسم القالب":</b> الواجهة الرسمية لا
 * تسمح بنصّ حرّ إلا داخل أربع وعشرين ساعة من آخر رسالة أرسلها ولي الأمر إلى السنتر.
 * وإشعارات الغياب والمتأخرات تبدأ من السنتر لا من ولي الأمر، فتكون خارج النافذة دائماً
 * تقريباً، ويردّ الخادم بالخطأ 131047. الحلّ المعتمد عند Meta هو قالب معتمَد مسبقاً،
 * ولهذا: إن ضُبط اسم قالب أُرسلت الرسالة قالباً بنصّها معاملاً أول {{1}}، وإلا أُرسلت
 * نصاً حرّاً — وهو ما يصلح للردّ على من راسل السنتر توّاً وللاختبار.</p>
 */
@Component
@RequiredArgsConstructor
public class WhatsAppCloudApiSender implements ChannelSender {

    /** عنوان Meta حين لا يُضبط شيء؛ الإصدار جزء من المسار عندهم */
    public static final String DEFAULT_BASE_URL = "https://graph.facebook.com/v21.0";

    /** ما يُعرض من ردّ المزوّد عند الفشل: يكفي لتمييز السبب ولا يملأ نافذة الخطأ */
    private static final int MAX_ERROR_BODY = 400;

    private final HttpPoster httpPoster;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP_CLOUD_API;
    }

    @Override
    public Optional<String> configurationProblem(NotificationConfig config) {
        if (!config.hasSenderId()) {
            return Optional.of(I18n.get("error.notification.senderIdMissing"));
        }
        if (!config.hasToken()) {
            return Optional.of(I18n.get("error.notification.tokenMissing"));
        }
        return Optional.empty();
    }

    @Override
    public MessageSender.SendResult send(NotificationConfig config, String internationalPhone, String message) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + config.apiToken());
        headers.put("Content-Type", "application/json; charset=utf-8");

        try {
            HttpPoster.Response response = httpPoster.post(endpoint(config), headers, body(config, internationalPhone, message));
            if (response.isSuccess()) {
                return MessageSender.SendResult.ok();
            }
            // نصّ الخطأ من Meta نفسه: "قالب غير معتمَد" و"مفتاح منتهٍ" و"خارج النافذة"
            // أخطاء مختلفة تماماً، و"فشل الإرسال" وحدها لا تدلّ على أيٍّ منها
            return MessageSender.SendResult.failed(I18n.format("error.notification.providerRejected",
                    response.status(), trim(response.body())));
        } catch (Exception e) {
            return MessageSender.SendResult.failed(
                    I18n.format("error.notification.requestFailed", messageOf(e)));
        }
    }

    /**
     * عنوان الإرسال: {@code {base}/{senderId}/messages}.
     * ويُقبل عنوان كامل ينتهي بـ {@code /messages} كما هو، لمن يمرّ بوسيط أو نسخة أخرى
     * من الواجهة — أفضل من أن يجد المستخدم عنوانه وقد أُلحق به مسار ثانٍ.
     */
    String endpoint(NotificationConfig config) {
        String base = config.hasApiUrl() ? config.apiUrl().trim() : DEFAULT_BASE_URL;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/messages")) {
            return base;
        }
        return base + "/" + config.senderId().trim() + "/messages";
    }

    /** جسم الطلب: قالباً معتمَداً إن ضُبط اسمه، وإلا نصاً حرّاً */
    String body(NotificationConfig config, String internationalPhone, String message) {
        String to = "\"to\":\"" + escape(internationalPhone) + "\"";

        if (!config.hasTemplateName()) {
            return "{\"messaging_product\":\"whatsapp\"," + to + ",\"type\":\"text\","
                    + "\"text\":{\"preview_url\":false,\"body\":\"" + escape(message) + "\"}}";
        }

        return "{\"messaging_product\":\"whatsapp\"," + to + ",\"type\":\"template\","
                + "\"template\":{\"name\":\"" + escape(config.templateName().trim()) + "\","
                + "\"language\":{\"code\":\"" + escape(config.templateLanguageOrDefault()) + "\"},"
                + "\"components\":[{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"text\":\"" + escape(message) + "\"}]}]}}";
    }

    /**
     * تهريب نصّ داخل JSON.
     *
     * <p>مكتوب هنا لا بمكتبة: المشروع لا يعتمد Jackson، والطلب كائن واحد معروف الشكل.
     * ما يُهرَّب ليس نظرياً — نصّ الإشعار يحمل سطراً جديداً واسم مجموعة قد يحوي علامة
     * اقتباس، وأيٌّ منهما يكسر الطلب فيردّ المزوّد خطأ لا علاقة له بالسبب.</p>
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String trim(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.length() <= MAX_ERROR_BODY ? body : body.substring(0, MAX_ERROR_BODY) + "…";
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
