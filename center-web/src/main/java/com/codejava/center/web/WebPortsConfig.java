package com.codejava.center.web;

import com.codejava.center.core.backup.BackupTarget;
import com.codejava.center.core.print.SheetHeaderPolicy;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.core.ui.UiDispatcher;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.service.notification.MessagingLinkPreferences;
import com.codejava.center.service.notification.WhatsAppLinkStyle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * أجوبةُ الخادم عن المنافذ التي تسألها طبقة الأعمال.
 *
 * <p>الجدول في {@code CLAUDE.md} يذكر لكل منفذٍ جوابَ الجهاز؛ هذه أجوبةُ الطرف الآخر،
 * وكلُّها أبسط من نظيرها هناك - وذلك هو الدليل على أن الحدّ في موضعه: ما كان معقّداً
 * على الجهاز كان معقّداً لأنه يخصّ الجهاز، لا لأن طبقة الأعمال تحتاجه.</p>
 */
@Configuration
public class WebPortsConfig {

    /** هويةُ الطلب من سياق الأمان، لا هوية البرنامج */
    @Bean
    public CurrentActor serverCurrentActor() {
        return new ServerCurrentActor();
    }

    /** المنفذان السرّيان في bean واحد: مصدرهما واحد، وفصلهما ملفّان يقرآن بيئةً واحدة */
    @Bean
    public EnvironmentSecrets environmentSecrets(Environment environment) {
        return new EnvironmentSecrets(environment);
    }

    @Bean
    public BackupTarget serverBackupTarget(CurrentCentre centre,
                                           ObjectProvider<TenantRegistry> registry) {
        return new ServerBackupTarget(centre, registry);
    }

    /**
     * الترويسة تُطبع دائماً.
     *
     * <p>سببُ إطفائها على الجهاز أن <b>هذه الطابعة</b> محمَّلة بورقٍ مطبوعٍ عليه
     * ترويسة السنتر - صفةُ درج ورق، ولذلك هي تفضيلٌ لكل جهاز. والخادم يسلّم PDF عبر
     * الشبكة ولا يعرف على أيّ ورقٍ يُطبع، إن طُبع أصلاً؛ وملفٌّ بلا ترويسة لا يُعرف
     * صاحبه بعد أن يُحفظ أو يُرسل.</p>
     */
    @Bean
    public SheetHeaderPolicy serverSheetHeaderPolicy() {
        return () -> true;
    }

    /**
     * لا خيط واجهة على خادم: المهمّة تُنفَّذ حيث وُلدت.
     *
     * <p>ما يقابل {@code Platform.runLater} هنا هو {@code SseEmitter.send}، وهي آمنةٌ
     * من أيّ خيط. والقفزةُ إلى خيطٍ آخر تعني فقدان سياق المؤسسة الذي ربطه الطلب -
     * أي تسليمَ تنبيهات سنترٍ إلى من يراقب سنتراً آخر.</p>
     */
    @Bean
    public UiDispatcher serverUiDispatcher() {
        return Runnable::run;
    }

    /**
     * قناة الرابط اليدوي على الويب: الرابط يُبنى ويُسلَّم في الجواب، ومن يفتحه هو
     * المتصفّح الذي أمامه إنسان - لا الخادم.
     *
     * <p>ولذلك {@link WhatsAppLinkStyle#WA_ME} لا {@code DESKTOP_APP}: الأخير يفتح
     * تطبيقاً مثبَّتاً على الجهاز، وأيّ جهاز يفتح الشاشة غيرُ معروف هنا.</p>
     */
    @Bean
    public MessagingLinkPreferences serverMessagingLinkPreferences() {
        return new MessagingLinkPreferences() {
            @Override
            public WhatsAppLinkStyle linkStyle() {
                return WhatsAppLinkStyle.WA_ME;
            }

            @Override
            public String linkTemplate() {
                return WhatsAppLinkStyle.WA_ME.template();
            }
        };
    }
}
