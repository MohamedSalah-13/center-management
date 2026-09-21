package com.codejava.center.web;

import com.codejava.center.core.backup.OffsiteBackup;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

/**
 * إلى أين تخرج نسخةُ هذا الخادم: مستودعٌ واحد من بيئة التشغيل، ومجلَّدٌ لكل سنتر.
 *
 * <p>وهذا هو الفرق الذي يوجد الرفعُ لأجله: {@code ServerBackupTarget.backupRoot} يكتب
 * الملفَّ تحت مجلَّد المؤسسة على قرص المضيف - وهو القرص الذي تجلس عليه قواعدُ الخمسين
 * جميعاً. فعطبُه يأخذ القواعدَ والنسخَ في لحظة، والاحتياطيُّ الذي يموت مع أصله ليس
 * احتياطياً.</p>
 *
 * <p>والعنوانُ والرمزُ من البيئة لا من إعدادات السنتر، ولسببين مختلفين: <b>الرمزُ</b>
 * سرٌّ يفتح مستودعاً فيه نسخُ الخمسين، وحفظُه في قاعدةٍ يشحنه مع كل نسخة تُؤخذ منها -
 * نفسُ حجّة كلمة مرور التشفير. <b>والعنوانُ</b> قرارُ من ينشر لا قرارُ كل سنتر: تركُه
 * في خمسين شاشة يعني خمسين عميلاً يُطلب من كلٍّ منهم أن يشتري تخزيناً ويكتب رابطه.</p>
 *
 * <p>والمجلَّدُ اسمُ مخطَّط المؤسسة - وهو نفسُه الذي مرّ على {@code SchemaName} حرفاً
 * حرفاً قبل أن يدخل {@code CREATE DATABASE}، فيصلح اسمَ مجلَّدٍ كذلك. وبدونه تجتمع
 * نسخُ السناتر في جذرٍ واحد بأسماءٍ تحمل الختمَ الزمني وحده، فنسختان أُخذتا في الثانية
 * نفسها تكتب إحداهما فوق الأخرى - عطبٌ لا يُكتشف إلا يوم الاستعادة.</p>
 */
public class ServerOffsiteBackup implements OffsiteBackup {

    /** عنوان مستودع النسخ خارج الخادم */
    static final String ENDPOINT = "CENTER_BACKUP_OFFSITE_URL";

    /** رمز الدخول إليه */
    static final String TOKEN = "CENTER_BACKUP_OFFSITE_TOKEN";

    private final Environment environment;

    private final CurrentCentre centre;

    /** سجلّ المنصة إن وُجد؛ غائبٌ على خادمٍ يخدم سنتراً واحداً */
    private final ObjectProvider<TenantRegistry> registry;

    public ServerOffsiteBackup(Environment environment, CurrentCentre centre,
                               ObjectProvider<TenantRegistry> registry) {
        this.environment = environment;
        this.centre = centre;
        this.registry = registry;
    }

    @Override
    public String endpoint() {
        return blankToNull(environment.getProperty(ENDPOINT));
    }

    @Override
    public char[] token() {
        String value = blankToNull(environment.getProperty(TOKEN));
        return value == null ? null : value.toCharArray();
    }

    /**
     * مجلَّدُ المؤسسة الجارية، أو {@code null} على تركيبٍ بسنترٍ واحد فيُرفع إلى الجذر.
     *
     * <p>والجذرُ هناك جوابٌ صحيح: خادمٌ لسنترٍ واحد لا يزاحمه أحد في المستودع.</p>
     */
    @Override
    public String folder() {
        TenantId tenant = centre.boundOrNull();
        TenantRegistry platform = registry.getIfAvailable();
        if (tenant == null || platform == null) {
            return null;
        }
        return platform.find(tenant).map(PlatformTenant::schema).map(schema -> schema.value())
                .orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
