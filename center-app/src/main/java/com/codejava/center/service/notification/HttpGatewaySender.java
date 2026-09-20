package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * إرسال عبر مزوّد رسائل خارجي يُوصف طلبه في الإعدادات بدل أن يُكتب صنف لكل مزوّد.
 *
 * <p>معظم السناتر في مصر لا تشترك في واجهة Meta الرسمية بل عند وسيط محلي يبيع واتساب أو
 * SMS بواجهة HTTP بسيطة، وكلٌّ يسمّي حقوله كما يشاء ({@code to} أو {@code phone} أو
 * {@code number}). صنف لكل مزوّد يعني إصداراً جديداً من البرنامج كلما اشترك سنتر عند
 * وسيط آخر — بينما هو في الحقيقة اختلاف في نصّ طلب واحد.</p>
 *
 * <p>القالب يحمل مواضع تُملأ: {@code {phone}} و{@code {message}} و{@code {token}} و
 * {@code {sender}}، في العنوان وفي جسم الطلب معاً. وشكل الجسم يُستنتج من أوله: بادئة
 * {@code &#123;} تعني JSON، وما عداه نموذج مُرمَّز — لأن الترميز الخاطئ يبتر الرسالة عند
 * أول مسافة أو علامة اقتباس.</p>
 *
 * <p>المفتاح داخل القالب حين ذُكر {@code {token}}، وإلا فترويسة
 * {@code Authorization: Bearer}. القاعدة صريحة لأن الوسطاء ينقسمون بين الأسلوبين،
 * وإرسال المفتاح في الاثنين معاً يرفضه بعضهم.</p>
 */
@Component
@RequiredArgsConstructor
public class HttpGatewaySender implements ChannelSender {

    public static final String PHONE = "{phone}";
    public static final String MESSAGE = "{message}";
    public static final String TOKEN = "{token}";
    public static final String SENDER = "{sender}";

    /** قالب يصلح لأشهر الوسطاء، ونقطة بداية يعدّلها المستخدم لا صفحة فارغة */
    public static final String DEFAULT_BODY_TEMPLATE = "token={token}&to={phone}&body={message}";

    private static final int MAX_ERROR_BODY = 400;

    private final HttpPoster httpPoster;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.HTTP_GATEWAY;
    }

    @Override
    public Optional<String> configurationProblem(NotificationConfig config) {
        if (!config.hasApiUrl()) {
            return Optional.of(I18n.get("error.notification.apiUrlMissing"));
        }
        if (!bodyTemplate(config).contains(MESSAGE)) {
            // قالب بلا موضع للنص يرسل طلباً ناجحاً برسالة فارغة: أسوأ من الفشل الصريح
            return Optional.of(I18n.format("error.notification.templateMissing", MESSAGE));
        }
        if (!requestTarget(config).contains(PHONE) && !bodyTemplate(config).contains(PHONE)) {
            return Optional.of(I18n.format("error.notification.templateMissing", PHONE));
        }
        return Optional.empty();
    }

    @Override
    public MessageSender.SendResult send(NotificationConfig config, String internationalPhone, String message) {
        String template = bodyTemplate(config);
        boolean json = isJson(template);

        UnaryOperator<String> encode = json
                ? WhatsAppCloudApiSender::escape
                : value -> URLEncoder.encode(value, StandardCharsets.UTF_8);

        String url = fill(requestTarget(config), config, internationalPhone, message,
                value -> URLEncoder.encode(value, StandardCharsets.UTF_8));
        String body = fill(template, config, internationalPhone, message, encode);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", json
                ? "application/json; charset=utf-8"
                : "application/x-www-form-urlencoded; charset=utf-8");
        if (config.hasToken() && !template.contains(TOKEN) && !requestTarget(config).contains(TOKEN)) {
            headers.put("Authorization", "Bearer " + config.apiToken());
        }

        try {
            HttpPoster.Response response = httpPoster.post(url, headers, body);
            if (response.isSuccess()) {
                return MessageSender.SendResult.ok();
            }
            return MessageSender.SendResult.failed(I18n.format("error.notification.providerRejected",
                    response.status(), trim(response.body())));
        } catch (Exception e) {
            return MessageSender.SendResult.failed(
                    I18n.format("error.notification.requestFailed", messageOf(e)));
        }
    }

    private String requestTarget(NotificationConfig config) {
        return config.hasApiUrl() ? config.apiUrl().trim() : "";
    }

    private String bodyTemplate(NotificationConfig config) {
        return config.hasBodyTemplate() ? config.bodyTemplate().trim() : DEFAULT_BODY_TEMPLATE;
    }

    /**
     * الجسم الذي يبدأ بكائن JSON؛ وما عداه نموذج {@code key=value}.
     * لا يكفي القوس وحده: قالب النموذج قد يبدأ هو الآخر بموضع مثل {@code {token}=...}،
     * فالفارق هو ما بعد القوس — علامة اقتباس مفتاح، أو قوس إغلاق لكائن فارغ.
     */
    static boolean isJson(String template) {
        if (!template.startsWith("{")) {
            return false;
        }
        String rest = template.substring(1).stripLeading();
        return rest.startsWith("\"") || rest.startsWith("}");
    }

    private String fill(String template, NotificationConfig config, String phone, String message,
                        UnaryOperator<String> encode) {
        return template
                .replace(PHONE, encode.apply(phone == null ? "" : phone))
                .replace(MESSAGE, encode.apply(message == null ? "" : message))
                .replace(SENDER, encode.apply(config.hasSenderId() ? config.senderId().trim() : ""))
                .replace(TOKEN, encode.apply(config.hasToken() ? config.apiToken() : ""));
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
