package com.codejava.center.util;

import com.codejava.center.config.StageReadyEvent;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * إشعار ويندوز من شريط المهام.
 *
 * <p>هو السطح الوحيد الذي يصل المستخدم <b>والبرنامج مصغَّر أو خلف نافذة أخرى</b>.
 * البطاقة المنبثقة داخل البرنامج تفترض أن أحداً ينظر إليه، وهو افتراض لا يصحّ في
 * السنتر: الجهاز يعمل طوال اليوم وصاحبه يفتح شاشات أخرى. فشلُ نسخة احتياطية يجب أن
 * يصل الآن لا حين يعود أحدٌ إلى البرنامج.</p>
 *
 * <h2>ثلاثة قيود يجب أن تُعرف قبل تعديل هذا الصنف</h2>
 *
 * <ul>
 *   <li><b>AWT لا JavaFX.</b> ولذلك يجب ألا يكون التطبيق في وضع headless -
 *       {@code JavaFxApplication} يعطّله صراحةً على باني Spring، لأن Spring Boot يشغّله
 *       افتراضياً. بدونه ترمي {@code SystemTray.getSystemTray()}
 *       {@code HeadlessException} عند أول تنبيه.</li>
 *   <li><b>لا يدعم الاتجاه من اليمين لليسار.</b> نصّ الفقاعة عربيّ ويظهر مرصوصاً من
 *       اليسار، وهذا قيد ويندوز نفسه لا خيارٌ هنا. النصّ مقروء، والترتيب فقط هو
 *       المخالف - ولذلك يُقصَّر النصّ إلى سطر واحد قصير، والتفصيل مكانه البرنامج.</li>
 *   <li><b>قد يُخفيه المستخدم أو النظام.</b> "مساعدة التركيز" في ويندوز تبتلع الفقاعات
 *       بلا أي إشارة للتطبيق. فلا يُبنى على وصولها شيء: كل ما يظهر هنا موجود أيضاً في
 *       صندوق التنبيهات، وهذا تذكيرٌ لا قناة تسليم.</li>
 * </ul>
 *
 * <p>الأيقونة تُركَّب عند أول إشعار فعلي لا عند الإقلاع: جهازٌ عُطّلت عليه هذه الميزة
 * لا ينبغي أن يحمل أيقونةً في شريط مهامه لا تفعل شيئاً.</p>
 */
@Component
public class TrayNotifier {

    private static final Logger log = LoggerFactory.getLogger(TrayNotifier.class);

    /** أقصى طول لنصّ الفقاعة؛ ويندوز يقصّها بلا رحمة وبلا علامة قصّ */
    private static final int MAX_TEXT = 180;

    private volatile TrayIcon trayIcon;
    private volatile Stage stage;

    /** مرجع النافذة، لإعادتها إلى الواجهة عند النقر المزدوج على الأيقونة */
    @EventListener(StageReadyEvent.class)
    public void onStageReady(StageReadyEvent event) {
        this.stage = event.getStage();
    }

    /**
     * يعرض فقاعة في شريط المهام.
     *
     * <p>لا يرمي شيئاً أبداً: يُستدعى من مسار عرض تنبيه، وخطؤه هنا يعني أن التنبيه
     * كلّه سقط - بينما هو أضعف سطوح العرض الثلاثة وأقلّها ضماناً.</p>
     *
     * @param critical الدرجة الحرجة تُعرض بأيقونة خطأ فيلوّنها ويندوز حمراء
     */
    public void notifyUser(String title, String message, boolean critical) {
        if (!AlertPreferences.trayEnabled() || !SystemTray.isSupported()) {
            return;
        }

        // كل ما يخصّ AWT على خيطه: استدعاؤه من خيط JavaFX يعلّق أحدهما على الآخر
        EventQueue.invokeLater(() -> {
            try {
                TrayIcon icon = installedIcon();
                if (icon != null) {
                    icon.displayMessage(title, clip(message),
                            critical ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
                }
            } catch (RuntimeException e) {
                log.debug("تعذّر عرض إشعار شريط المهام: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void remove() {
        TrayIcon icon = trayIcon;
        if (icon != null) {
            // بدونها تبقى أيقونة ميتة في شريط المهام حتى يمرّ المؤشر فوقها
            EventQueue.invokeLater(() -> SystemTray.getSystemTray().remove(icon));
            trayIcon = null;
        }
    }

    private TrayIcon installedIcon() {
        TrayIcon existing = trayIcon;
        if (existing != null) {
            return existing;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            TrayIcon icon = new TrayIcon(appIcon(tray.getTrayIconSize()), I18n.get("app.title"));
            icon.setImageAutoSize(true);

            // النقر على الفقاعة أو على الأيقونة يعيد النافذة: من رأى الإشعار
            // يريد أن ينظر فيه، وبحثه عن البرنامج في شريط المهام خطوة زائدة
            icon.addActionListener(event -> Platform.runLater(this::bringWindowToFront));

            tray.add(icon);
            trayIcon = icon;
            return icon;
        } catch (AWTException | RuntimeException e) {
            // بيئة بلا شريط مهام، أو نظام رفض الإضافة: البطاقة داخل البرنامج تكفي
            log.debug("تعذّر تركيب أيقونة شريط المهام: {}", e.getMessage());
            return null;
        }
    }

    private void bringWindowToFront() {
        Stage current = stage;
        if (current != null) {
            current.setIconified(false);
            current.toFront();
            current.requestFocus();
        }
    }

    /**
     * أيقونة مرسومة لا ملف صورة.
     *
     * <p>المشروع لا يشحن أي أصل رسومي - شعار السنتر يختاره العميل ويُحفظ مساراً في
     * الإعدادات، وقد يكون غير موجود أو بمقاس لا يصلح لشريط المهام. حرفٌ واحد على
     * خلفية داكنة يبقى مقروءاً عند ١٦ نقطة، وهو ما لا يضمنه شعارٌ مصمَّم للمطبوعات.</p>
     */
    private BufferedImage appIcon(Dimension size) {
        int width = Math.max(16, size.width);
        int height = Math.max(16, size.height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(new java.awt.Color(0x2C, 0x3E, 0x50));
        graphics.fill(new RoundRectangle2D.Double(0, 0, width, height, width / 3.0, height / 3.0));

        // حرف لاتيني لا عربي: رسم العربية عند ١٦ نقطة يخرج لطخةً على أغلب الخطوط
        graphics.setColor(java.awt.Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (height * 0.68)));

        String glyph = "C";
        int textWidth = graphics.getFontMetrics().stringWidth(glyph);
        int baseline = (height + graphics.getFontMetrics().getAscent()) / 2 - 1;
        graphics.drawString(glyph, (width - textWidth) / 2, baseline);

        graphics.dispose();
        return image;
    }

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "…";
    }
}
