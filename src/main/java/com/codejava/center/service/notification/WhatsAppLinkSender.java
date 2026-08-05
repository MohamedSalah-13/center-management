package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.util.I18n;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;

/**
 * إرسال بفتح محادثة واتساب على هذا الجهاز بالنص جاهزاً، ويضغط الموظف "إرسال".
 *
 * <p>القناة الافتراضية لأنها تعمل فوراً بحساب واتساب عادي: لا اشتراك ولا موافقة مزوّد
 * ولا تكلفة لكل رسالة، ولا حدّ زمني كنافذة الأربع والعشرين ساعة في الواجهة الرسمية.</p>
 *
 * <p>وهي <b>يدوية بطبيعتها</b>: تفتح نافذة لكل ولي أمر، فلا تصلح لمئة رسالة. كون
 * المستخدم هو من يضغط "إرسال" ميزة لا قيد — لا يستطيع النظام مراسلة أولياء الأمور دون
 * رؤية بشرية للنص والرقم. من يحتاج الإرسال الصامت يختار قناة مزوّد.</p>
 *
 * <p>شكل الرابط يأتي من {@link NotificationConfig} لا من الكود: راجع
 * {@link WhatsAppLinkStyle} لسبب كونه اختياراً لكل جهاز.</p>
 */
@Component
public class WhatsAppLinkSender implements ChannelSender {

    /**
     * فتح الرابط، مفصولاً ليُختبر بلا متصفح.
     * {@link Desktop} صنف ثابت لا يمكن استبداله، والاختبار البديل هو ألا يُختبر شيء.
     */
    @FunctionalInterface
    public interface LinkOpener {
        void open(URI uri) throws Exception;
    }

    private final LinkOpener opener;

    public WhatsAppLinkSender() {
        this(WhatsAppLinkSender::openWithDesktop);
    }

    public WhatsAppLinkSender(LinkOpener opener) {
        this.opener = opener;
    }

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
        String url;
        try {
            url = WhatsAppLink.build(config.linkStyle(), config.linkTemplate(), internationalPhone, message);
        } catch (IllegalArgumentException e) {
            return MessageSender.SendResult.failed(e.getMessage());
        }

        try {
            opener.open(URI.create(url));
            return MessageSender.SendResult.ok();
        } catch (Exception e) {
            return MessageSender.SendResult.failed(
                    I18n.format("error.whatsapp.openFailed", messageOf(e)));
        }
    }

    /**
     * يفتح الرابط بمعالج البروتوكول في نظام التشغيل.
     *
     * <p>{@link Desktop#browse} يمرّ عبر {@code ShellExecute} على ويندوز فيفتح
     * {@code whatsapp://} كما يفتح {@code https://}، لكنه غير مضمون على كل نسخة —
     * وحين يرفض، البديل هو نفس المعالج مستدعىً مباشرةً. بدون هذا البديل يظهر لمن اختار
     * "تطبيق واتساب" فشلٌ لا يفهم سببه بينما التطبيق مثبَّت أمامه.</p>
     */
    private static void openWithDesktop(URI uri) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
                return;
            } catch (Exception e) {
                if (!isWindows()) {
                    throw e;
                }
            }
        } else if (!isWindows()) {
            throw new UnsupportedOperationException(I18n.get("error.whatsapp.browserUnsupported"));
        }

        new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
