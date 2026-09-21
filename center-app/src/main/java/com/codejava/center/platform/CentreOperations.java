package com.codejava.center.platform;

import com.codejava.center.core.alert.AlertSchedule;
import com.codejava.center.core.backup.BackupSchedule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.service.BackupSchedules;
import com.codejava.center.service.alert.AlertSchedules;

import java.time.LocalDateTime;

/**
 * حالُ سنترٍ واحد كما يراها مشغّلُ المنصة: أتعمل مهامُّه التلقائية، ومتى آخرَ ما عملت.
 *
 * <p>السؤال الذي يجيب عنه واحد: <b>أيُّ سنترٍ أظلم؟</b> على جهازٍ في سنتر يجيب عنه
 * صاحبُه من شاشة إعداداته - تاريخٌ قديم في "آخر نسخة" هو كل ما يقوله إن الليالي تمرّ
 * بلا نسخة. وعلى منصّةٍ تخدم خمسين سنتراً لا أحد ينظر في تلك الشاشة الخمسين، فيبقى
 * السنترُ الذي توقّفت نسخُه منذ شهر يعمل تماماً - حتى اليوم الذي تُطلب فيه نسخته.</p>
 *
 * <h2>ثلاثة قرارات مكتوبة هنا لا في الشاشة</h2>
 *
 * <ul>
 *   <li><b>الموقوفُ ليس متأخراً.</b> {@link TenantStatus#isServed()} يقرّر ألا تعمل
 *       المهامُّ التلقائية لمن توقّف اشتراكه، فوسمُه "متأخر" يعني خمسين إنذاراً كاذباً
 *       في مسحٍ واحد - ثم مشغّلاً يتعلّم تجاهُل اللون الأحمر فيه، وهو بالضبط ما وُجد
 *       المسحُ ليمنعه.</li>
 *   <li><b>وما لم يُطلب لا يتأخّر.</b> سنترٌ أغلق النسخَ التلقائي اختار ذلك؛ ونسخةٌ
 *       "فائتة" لمن لم يطلبها ليست خبراً. الفرقُ بين "مطفأ" و"متأخر" هو الفرق بين
 *       قرارٍ وعطل.</li>
 *   <li><b>والتأخّرُ يُحسب بموعد السنتر نفسه</b> عبر {@link BackupSchedule#isOverdue}
 *       و{@link AlertSchedule#isOverdue}، لا بـ"مضى يوم": سنترٌ ينسخ شهرياً ليس
 *       متأخراً في اليوم الثاني، وثانٍ ينسخ يومياً متأخرٌ فيه. وهما الحسابان
 *       اللذان يعملان في المجدوِل نفسه، فلا يقول المسحُ شيئاً ويفعل المجدوِل غيره.</li>
 * </ul>
 *
 * <p>و{@link #readable} هو الحقل الذي يجعل الغياب دليلاً: سنترٌ تعذّرت قراءةُ قاعدته
 * يبقى في المسح موسوماً بسببه، ولا يسقط منه. سقوطُه يجعل قائمةً من تسعةٍ وأربعين تبدو
 * سليمةً تماماً، والخمسون هو الذي كان ينبغي أن يُرى.</p>
 *
 * @param openCritical تنبيهاتٌ حرجة لم تُعالَج بعد - لا مجموعُ التنبيهات: إيصالاتُ الدفع
 *                     وحدها عشراتٌ في اليوم، ومجموعٌ يعدّها يُقرأ رقماً كبيراً دائماً
 *                     فلا يقول شيئاً
 */
public record CentreOperations(
        long id, String name, String slug, TenantStatus status,
        boolean readable, String problem,
        boolean autoBackupEnabled, LocalDateTime lastBackupAt, boolean backupOverdue,
        boolean alertsEnabled, LocalDateTime lastScanAt, boolean scanOverdue,
        long openCritical) {

    /** حالُ سنترٍ قُرئت إعداداتُه */
    public static CentreOperations of(PlatformTenant tenant, CenterSettings settings,
                                      long openCritical, LocalDateTime now) {
        boolean served = tenant.status().isServed();

        boolean autoBackup = settings.isAutoBackupEnabled();
        boolean backupOverdue = served && autoBackup
                && BackupSchedules.from(settings).isOverdue(settings.getLastAutoBackupAt(), now);

        boolean alerts = settings.isAlertsEnabled();
        boolean scanOverdue = served && alerts
                && AlertSchedules.from(settings).isOverdue(settings.getLastAlertScanAt(), now);

        return new CentreOperations(tenant.id().value(), tenant.name(), tenant.slug(),
                tenant.status(), true, null,
                autoBackup, settings.getLastAutoBackupAt(), backupOverdue,
                alerts, settings.getLastAlertScanAt(), scanOverdue,
                openCritical);
    }

    /**
     * حالُ سنترٍ تعذّرت قراءتُه.
     *
     * <p>لا شيء يُدَّعى عنه: لا "غير متأخر" ولا "صفر تنبيهات" - كلاهما جوابٌ يبدو
     * مطمئناً عن سؤالٍ لم يُسأل أصلاً.</p>
     */
    public static CentreOperations unreadable(PlatformTenant tenant, String problem) {
        return new CentreOperations(tenant.id().value(), tenant.name(), tenant.slug(),
                tenant.status(), false, problem,
                false, null, false,
                false, null, false,
                0);
    }

    /** أثمّة ما يستدعي نظرَ المشغّل في هذا السنتر الآن؟ */
    public boolean needsAttention() {
        return !readable || backupOverdue || scanOverdue || openCritical > 0;
    }
}
