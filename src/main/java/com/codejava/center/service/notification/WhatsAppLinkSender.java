package com.codejava.center.service.notification;

import com.codejava.center.util.I18n;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * أو بوابة SMS، ويُفعَّل بضبط {@code center.notifications.channel} على قيمته.</p>
 *
 * <p>الاختيار بخاصية صريحة لا بـ {@code @ConditionalOnMissingBean}: ذلك التعليق
 * على صنف يُلتقط بمسح المكوّنات يُقيَّم مقابل تعريف الصنف نفسه فيستبعد نفسه،
 * فلا يُسجَّل أي bean ويفشل إقلاع التطبيق. هو مخصص لأصناف التهيئة التلقائية.</p>
 *
 * <p>كون المستخدم هو من يضغط "إرسال" ميزة لا قيد: لا يمكن للنظام أن يرسل رسائل
 * لأولياء الأمور دون رؤية بشرية للنص والرقم.</p>
 */
@Component
@ConditionalOnProperty(
        name = "center.notifications.channel",
        havingValue = "whatsapp-link",
        matchIfMissing = true)
public class WhatsAppLinkSender implements MessageSender {

    @Override
    public SendResult send(String internationalPhone, String message) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return SendResult.failed(I18n.get("error.whatsapp.browserUnsupported"));
            }

            String url = "https://wa.me/" + internationalPhone
                    + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(URI.create(url));
            return SendResult.ok();
        } catch (Exception e) {
            return SendResult.failed(I18n.format("error.whatsapp.openFailed", e.getMessage()));
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
