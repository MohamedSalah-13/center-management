package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * إرسال بفتح محادثة واتساب على جهاز الموظف بالنص جاهزاً، ويضغط هو "إرسال".
 *
 * <p>القناة الافتراضية لأنها تعمل فوراً بحساب واتساب عادي: لا اشتراك ولا موافقة مزوّد
 * ولا تكلفة لكل رسالة، ولا حدّ زمني كنافذة الأربع والعشرين ساعة في الواجهة الرسمية.</p>
 *
 * <p>وهي <b>يدوية بطبيعتها</b>: تفتح نافذة لكل ولي أمر، فلا تصلح لمئة رسالة. كون
 * المستخدم هو من يضغط "إرسال" ميزة لا قيد — لا يستطيع النظام مراسلة أولياء الأمور دون
 * رؤية بشرية للنص والرقم. من يحتاج الإرسال الصامت يختار قناة مزوّد.</p>
 *
 * <p><b>وهذا الصنف يبني الرابط ولا يفتحه.</b> كان يفتحه بنفسه عبر {@code java.awt.Desktop}،
 * فكانت طبقة الأعمال تفترض أن أمامها سطحَ مكتبٍ وتطبيقَ واتساب مثبَّتاً — وخادمٌ يبني نفس
 * الرابط ليضعه في جواب HTTP ليس أمامه شيء من ذلك. الآن يعود الرابط في
 * {@link MessageSender.SendResult#handOff(String)}، ومن يملك شاشةً يفتحه: على Desktop
 * {@code util/Links}، وعلى الويب متصفّح الموظف.</p>
 *
 * <p>شكل الرابط يأتي من {@link NotificationConfig} لا من الكود: راجع
 * {@link WhatsAppLinkStyle} لسبب كونه اختياراً لكل جهاز.</p>
 */
@Component
public class WhatsAppLinkSender implements ChannelSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP_LINK;
    }

    @Override
    public Optional<String> configurationProblem(NotificationConfig config) {
        try {
            // بناء رابط تجريبي يكشف القالب المعطوب الآن، لا عند أول ولي أمر
            WhatsAppLink.build(config.linkStyle(), config.linkTemplate(), "201000000000", "x");
        } catch (IllegalArgumentException e) {
            return Optional.of(e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public MessageSender.SendResult send(NotificationConfig config, String internationalPhone, String message) {
        try {
            return MessageSender.SendResult.handOff(
                    WhatsAppLink.build(config.linkStyle(), config.linkTemplate(),
                            internationalPhone, message));
        } catch (IllegalArgumentException e) {
            return MessageSender.SendResult.failed(e.getMessage());
        }
    }
}
