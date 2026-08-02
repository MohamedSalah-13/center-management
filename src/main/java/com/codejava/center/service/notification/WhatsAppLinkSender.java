package com.codejava.center.service.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * إرسال عبر رابط واتساب المباشر (wa.me).
 *
 * <p>القناة الافتراضية لأنها تعمل فوراً بحساب واتساب عادي: لا اشتراك ولا موافقة
 * مزوّد ولا تكلفة لكل رسالة. يفتح النظام محادثة الرقم بنص الرسالة جاهزاً، ويضغط
 * المستخدم "إرسال" بنفسه.</p>
 *
 * <p>هذا يعني أنها <b>يدوية بطبيعتها</b> ولا تصلح لإرسال مئات الرسائل. عند الوصول
 * لهذا الحجم يُضاف صنف آخر يطبّق {@link MessageSender} فوق WhatsApp Business API
 * أو بوابة SMS، ويحل محل هذا تلقائياً بفضل {@code @ConditionalOnMissingBean}.</p>
 *
 * <p>كون المستخدم هو من يضغط "إرسال" ميزة لا قيد: لا يمكن للنظام أن يرسل رسائل
 * لأولياء الأمور دون رؤية بشرية للنص والرقم.</p>
 */
@Component
@ConditionalOnMissingBean(MessageSender.class)
public class WhatsAppLinkSender implements MessageSender {

    @Override
    public SendResult send(String internationalPhone, String message) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return SendResult.failed("نظام التشغيل لا يدعم فتح المتصفح تلقائياً.");
            }

            String url = "https://wa.me/" + internationalPhone
                    + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(URI.create(url));
            return SendResult.ok();
        } catch (Exception e) {
            return SendResult.failed("تعذر فتح محادثة واتساب: " + e.getMessage());
        }
    }

    @Override
    public String channelName() {
        return "WHATSAPP_LINK";
    }

    @Override
    public boolean requiresManualConfirmation() {
        return true;
    }
}
