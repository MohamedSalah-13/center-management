package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ما يُرسل فعلاً إلى المزوّد.
 *
 * <p>الطلب نصّ يُبنى بالتركيب، وما يكسره لا يظهر عند البناء: علامة اقتباس في اسم مجموعة،
 * سطر جديد في نص الإشعار، مسافة غير مُرمَّزة في نموذج. النتيجة رسالة تصل مبتورة أو لا
 * تصل، ويردّ المزوّد بخطأ لا علاقة له بالسبب. لهذا تُفحص هنا بلا شبكة.</p>
 */
class ProviderSendersTest {

    private static final String PHONE = "201012345678";

    private final RecordingPoster poster = new RecordingPoster();
    private final WhatsAppCloudApiSender cloud = new WhatsAppCloudApiSender(poster);
    private final HttpGatewaySender gateway = new HttpGatewaySender(poster);

    // ------------------------------------------------------- واتساب الرسمي

    @Test
    void cloudEndpointIsBuiltFromTheSenderIdWhenNoUrlIsConfigured() {
        cloud.send(cloudConfig(null, null), PHONE, "x");

        assertThat(poster.url)
                .isEqualTo(WhatsAppCloudApiSender.DEFAULT_BASE_URL + "/109876/messages");
    }

    /** من يمرّ بوسيط يكتب العنوان كاملاً؛ إلحاق مسار ثانٍ به يجعله عنواناً لا وجود له */
    @Test
    void cloudKeepsAFullEndpointAsWritten() {
        cloud.send(cloudConfig("https://proxy.example.com/v1/109876/messages", null), PHONE, "x");

        assertThat(poster.url).isEqualTo("https://proxy.example.com/v1/109876/messages");
    }

    @Test
    void cloudSendsFreeTextWhenNoTemplateIsConfigured() {
        cloud.send(cloudConfig(null, null), PHONE, "مرحبا");

        assertThat(poster.body).contains("\"type\":\"text\"", "\"body\":\"مرحبا\"", "\"to\":\"" + PHONE + "\"");
        assertThat(poster.headers).containsEntry("Authorization", "Bearer TOKEN-123");
    }

    /**
     * خارج نافذة الأربع والعشرين ساعة لا تُقبل إلا القوالب، وإشعارات الغياب تبدأ من
     * السنتر فتكون خارجها دائماً تقريباً. نصّ الإشعار كله يذهب معاملاً أول.
     */
    @Test
    void cloudSendsAnApprovedTemplateWithTheMessageAsItsParameter() {
        cloud.send(cloudConfig(null, "absence_notice"), PHONE, "نص الإشعار");

        assertThat(poster.body).contains(
                "\"type\":\"template\"",
                "\"name\":\"absence_notice\"",
                "\"language\":{\"code\":\"ar\"}",
                "{\"type\":\"text\",\"text\":\"نص الإشعار\"}");
    }

    @Test
    void quotesAndNewLinesInTheMessageDoNotBreakTheJsonRequest() {
        cloud.send(cloudConfig(null, null), PHONE, "مجموعة \"أ\"\nسطر ثانٍ");

        assertThat(poster.body).contains("\\\"أ\\\"", "\\n");
    }

    @Test
    void cloudReportsTheProviderRejectionWithItsOwnMessage() {
        poster.respondWith(401, "{\"error\":{\"message\":\"expired token\"}}");

        MessageSender.SendResult result = cloud.send(cloudConfig(null, null), PHONE, "x");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("401", "expired token");
    }

    @Test
    void cloudRefusesToTryWithoutASenderIdOrAToken() {
        assertThat(cloud.configurationProblem(config(NotificationChannel.WHATSAPP_CLOUD_API,
                null, null, null, null, "TOKEN")))
                .contains(I18n.get("error.notification.senderIdMissing"));

        assertThat(cloud.configurationProblem(config(NotificationChannel.WHATSAPP_CLOUD_API,
                null, "109876", null, null, null)))
                .contains(I18n.get("error.notification.tokenMissing"));
    }

    // ------------------------------------------------------- المزوّد العام

    @Test
    void gatewayFillsTheDefaultFormTemplateAndEncodesTheMessage() {
        gateway.send(gatewayConfig(null), PHONE, "مرحبا يا أهلاً");

        assertThat(poster.headers).containsEntry("Content-Type",
                "application/x-www-form-urlencoded; charset=utf-8");
        assertThat(poster.body).startsWith("token=TOKEN-123&to=" + PHONE + "&body=");
        // المسافة غير المُرمَّزة تقطع النموذج فتصل الرسالة عند أول كلمة
        assertThat(poster.body).doesNotContain(" ").contains("%D9%85");
    }

    @Test
    void gatewaySendsJsonWhenTheTemplateIsJson() {
        gateway.send(gatewayConfig("{\"to\":\"{phone}\",\"text\":\"{message}\"}"), PHONE, "قال \"نعم\"");

        assertThat(poster.headers).containsEntry("Content-Type", "application/json; charset=utf-8");
        assertThat(poster.body).isEqualTo("{\"to\":\"" + PHONE + "\",\"text\":\"قال \\\"نعم\\\"\"}");
    }

    /** الوسطاء ينقسمون بين المفتاح في الجسم والمفتاح في الترويسة، وبعضهم يرفض الاثنين معاً */
    @Test
    void gatewayPutsTheTokenInTheHeaderOnlyWhenTheTemplateDoesNotAskForIt() {
        gateway.send(gatewayConfig("to={phone}&body={message}"), PHONE, "x");
        assertThat(poster.headers).containsEntry("Authorization", "Bearer TOKEN-123");

        gateway.send(gatewayConfig(null), PHONE, "x");
        assertThat(poster.headers).doesNotContainKey("Authorization");
    }

    @Test
    void gatewaySubstitutesPlaceholdersInsideTheUrlToo() {
        NotificationConfig config = config(NotificationChannel.HTTP_GATEWAY,
                "https://api.example.com/{sender}/messages", "inst-7", null, null, "TOKEN-123");

        gateway.send(config, PHONE, "x");

        assertThat(poster.url).isEqualTo("https://api.example.com/inst-7/messages");
    }

    @Test
    void gatewayRefusesATemplateThatWouldSendAnEmptyMessage() {
        assertThat(gateway.configurationProblem(gatewayConfig("to={phone}")))
                .contains(I18n.format("error.notification.templateMissing", HttpGatewaySender.MESSAGE));

        assertThat(gateway.configurationProblem(config(NotificationChannel.HTTP_GATEWAY,
                null, null, null, null, null)))
                .contains(I18n.get("error.notification.apiUrlMissing"));
    }

    @Test
    void gatewayReportsAnUnreachableProviderInsteadOfThrowing() {
        poster.failWith(new java.net.ConnectException("connection refused"));

        MessageSender.SendResult result = gateway.send(gatewayConfig(null), PHONE, "x");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("connection refused");
    }

    // ------------------------------------------------------------ مساعدات

    private NotificationConfig cloudConfig(String apiUrl, String templateName) {
        return config(NotificationChannel.WHATSAPP_CLOUD_API, apiUrl, "109876", templateName, null, "TOKEN-123");
    }

    private NotificationConfig gatewayConfig(String bodyTemplate) {
        return config(NotificationChannel.HTTP_GATEWAY, "https://api.example.com/send",
                "inst-7", null, bodyTemplate, "TOKEN-123");
    }

    private NotificationConfig config(NotificationChannel channel, String apiUrl, String senderId,
                                      String templateName, String bodyTemplate, String token) {
        return new NotificationConfig(channel, WhatsAppLinkStyle.WA_ME, null,
                apiUrl, senderId, templateName, null, bodyTemplate, token);
    }

    /** يلتقط الطلب بدل إرساله */
    private static class RecordingPoster implements HttpPoster {
        private String url;
        private Map<String, String> headers;
        private String body;
        private int status = 200;
        private String responseBody = "{\"messages\":[{\"id\":\"1\"}]}";
        private Exception failure;

        void respondWith(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        void failWith(Exception failure) {
            this.failure = failure;
        }

        @Override
        public Response post(String url, Map<String, String> headers, String body) throws Exception {
            this.url = url;
            this.headers = headers;
            this.body = body;
            if (failure != null) {
                throw failure;
            }
            return new Response(status, responseBody);
        }
    }
}
