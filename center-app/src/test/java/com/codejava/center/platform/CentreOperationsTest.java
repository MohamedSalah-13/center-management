package com.codejava.center.platform;

import com.codejava.center.core.backup.BackupFrequency;
import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.CenterSettings;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حالُ سنترٍ واحد في مسح المنصة: ما يُعدّ تأخّراً، وما لا يُعدّ.
 *
 * <p>ولا شيء هنا يحتاج Spring ولا قاعدة: القرار كلُّه صفّ إعدادات وحالُ اشتراكٍ
 * ولحظة. وهذا هو الغرض من فصله عن {@link PlatformOperations} - فالمسارُ الحقيقي
 * يحتاج MySQL بخمسين مخطَّطاً، والقرارُ هو ما يخطئ صامتاً.</p>
 *
 * <p>والخطأ هنا في اتجاهين كلاهما يُفقد المسحَ قيمته: إنذارٌ كاذب يعلّم المشغّل
 * تجاهُل اللون الأحمر، وصمتٌ عن سنترٍ توقّفت نسخُه شهراً.</p>
 */
class CentreOperationsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    /** اشتراكٌ مدفوع بعيداً: ما لم يكن الاشتراكُ موضوعَ الاختبار فهو ليس سببَ نتيجته */
    private static final LocalDate PAID_THROUGH = NOW.toLocalDate().plusMonths(6);

    /** نسخةٌ يومية آخرُها أمس ظهراً: موعدُ الثانية فجراً مرّ ولم تُؤخذ */
    @Test
    void aServedCentreWhoseNightlyBackupSlotPassedIsOverdue() {
        CentreOperations row = of(TenantStatus.ACTIVE,
                dailyBackupAt(LocalTime.of(2, 0), NOW.minusDays(1).withHour(12)), 0);

        assertThat(row.backupOverdue()).isTrue();
        assertThat(row.needsAttention()).isTrue();
    }

    /**
     * <b>والموقوفُ ليس متأخراً.</b>
     *
     * <p>{@link TenantStatus#isServed()} يقرّر ألا تعمل له المهامُّ التلقائية أصلاً،
     * فوسمُه "متأخر" إنذارٌ كاذب عن قرارٍ اتُّخذ عمداً - وخمسون منه في مسحٍ واحد
     * تُعلّم المشغّل ألا ينظر.</p>
     */
    @Test
    void aSuspendedCentreIsNeverOverdueBecauseNothingRunsForItAnyway() {
        CenterSettings stale = dailyBackupAt(LocalTime.of(2, 0), NOW.minusMonths(2));

        assertThat(of(TenantStatus.SUSPENDED, stale, 0).backupOverdue()).isFalse();
        assertThat(of(TenantStatus.CLOSED, stale, 0).backupOverdue()).isFalse();
        assertThat(of(TenantStatus.ACTIVE, stale, 0).backupOverdue())
                .as("والمخدومُ بنفس الصفّ متأخر - وإلا لم يكن الفرقُ حالَ الاشتراك")
                .isTrue();
    }

    /** وما لم يُطلب لا يتأخّر: سنترٌ أغلق النسخ التلقائي اختار ذلك */
    @Test
    void aCentreThatSwitchedAutomaticBackupOffIsNotOverdueHoweverOldTheStampIs() {
        CenterSettings settings = dailyBackupAt(LocalTime.of(2, 0), NOW.minusYears(1));
        settings.setAutoBackupEnabled(false);

        CentreOperations row = of(TenantStatus.ACTIVE, settings, 0);

        assertThat(row.backupOverdue()).isFalse();
        assertThat(row.autoBackupEnabled()).isFalse();
        assertThat(row.lastBackupAt())
                .as("الختمُ يُعرض على أي حال: 'مطفأ منذ سنة' جوابٌ يخصّ من يسأل")
                .isEqualTo(NOW.minusYears(1));
    }

    /**
     * والتأخّر بموعد السنتر نفسه لا بـ"مضى يوم".
     *
     * <p>سنترٌ ينسخ شهرياً ليس متأخراً بعد يومين، وثانٍ ينسخ يومياً متأخرٌ - والصفّان
     * يحملان الختمَ نفسه. وهما الحسابان اللذان يعملان في المجدوِل، فلا يقول المسحُ
     * شيئاً ويفعل المجدوِل غيره.</p>
     */
    @Test
    void overdueFollowsTheCentresOwnScheduleNotAFixedDay() {
        LocalDateTime twoDaysAgo = NOW.minusDays(2);

        CenterSettings monthly = dailyBackupAt(LocalTime.of(2, 0), twoDaysAgo);
        monthly.setBackupFrequency(BackupFrequency.MONTHLY);
        monthly.setBackupDayOfMonth(1);

        assertThat(of(TenantStatus.ACTIVE, monthly, 0).backupOverdue()).isFalse();
        assertThat(of(TenantStatus.ACTIVE, dailyBackupAt(LocalTime.of(2, 0), twoDaysAgo), 0)
                .backupOverdue()).isTrue();
    }

    /** وفحصُ التنبيهات له موعدُه هو، ويُقاس بنفس القاعدة */
    @Test
    void theAlertScanIsJudgedByItsOwnHourAndOnlyWhenItIsSwitchedOn() {
        CenterSettings settings = dailyBackupAt(LocalTime.of(2, 0), NOW);
        settings.setAlertsEnabled(true);
        settings.setAlertScanTime(LocalTime.of(8, 0));
        settings.setLastAlertScanAt(NOW.minusDays(3));

        assertThat(of(TenantStatus.ACTIVE, settings, 0).scanOverdue()).isTrue();

        settings.setAlertsEnabled(false);
        assertThat(of(TenantStatus.ACTIVE, settings, 0).scanOverdue()).isFalse();
    }

    /** ولا فحصَ وقع بعد: غيابُ الختم تأخّرٌ لا "لا شيء يُقال" */
    @Test
    void aCentreThatNeverRanEitherJobIsOverdueOnBoth() {
        CenterSettings fresh = CenterSettings.builder()
                .autoBackupEnabled(true)
                .alertsEnabled(true)
                .build();

        CentreOperations row = of(TenantStatus.ACTIVE, fresh, 0);

        assertThat(row.backupOverdue()).isTrue();
        assertThat(row.scanOverdue()).isTrue();
        assertThat(row.lastBackupAt()).isNull();
    }

    /** تنبيهٌ حرج قائم يستدعي النظر ولو كان كلُّ شيءٍ آخر في موعده */
    @Test
    void anOpenCriticalAlertIsEnoughToNeedAttention() {
        CentreOperations calm = of(TenantStatus.ACTIVE, upToDate(), 0);
        CentreOperations loud = of(TenantStatus.ACTIVE, upToDate(), 1);

        assertThat(calm.needsAttention()).isFalse();
        assertThat(loud.needsAttention()).isTrue();
        assertThat(loud.openCritical()).isEqualTo(1);
    }

    /**
     * والسنترُ الذي تعذّرت قراءتُه لا يدّعي عنه المسحُ شيئاً.
     *
     * <p>"غير متأخر" و"صفر تنبيهات" جوابان يبدوان مطمئنين عن سؤالٍ لم يُسأل، و
     * {@code readable} هو ما يفرّق بين "بخير" و"لا نعلم".</p>
     */
    @Test
    void anUnreadableCentreClaimsNothingAndStillNeedsAttention() {
        CentreOperations row = CentreOperations.unreadable(
                tenant(TenantStatus.ACTIVE), "قاعدة هذا السنتر مقطوعة");

        assertThat(row.readable()).isFalse();
        assertThat(row.problem()).isEqualTo("قاعدة هذا السنتر مقطوعة");
        assertThat(row.needsAttention()).isTrue();
        assertThat(row.lastBackupAt()).isNull();
        assertThat(row.lastScanAt()).isNull();
        assertThat(row.openCritical()).isZero();
    }

    /* ------------------------------------------------------- الاشتراك */

    /**
     * <b>والمنقضي اشتراكُه ليس متأخراً كذلك - للسبب نفسه.</b>
     *
     * <p>{@code isServedOn} يجمع المحورين: يسمح المشغّل <b>و</b>يكون مدفوعاً. فسنترٌ
     * انقضى اشتراكُه لا تعمل له نسخةٌ ولا فحص، ووسمُه "متأخر" يضيف إنذاراً كاذباً فوق
     * سببٍ معروف - والمطلوب أن يُقرأ سطرُه "لم يدفع" لا "تعطّلت نسخُه".</p>
     */
    @Test
    void aLapsedCentreIsNotOverdueEitherBecauseNothingRunsForItAnyway() {
        CenterSettings stale = dailyBackupAt(LocalTime.of(2, 0), NOW.minusMonths(2));
        LocalDate lapsed = NOW.toLocalDate().minusMonths(2);

        CentreOperations row = CentreOperations.of(
                tenant(TenantStatus.ACTIVE, lapsed), stale, 0, NOW);

        assertThat(row.lapsed()).isTrue();
        assertThat(row.backupOverdue())
                .as("السببُ واحد ويُقال مرة: لم يدفع")
                .isFalse();
        assertThat(row.needsAttention()).isTrue();
    }

    /** والباقي يُعرض بالأيام، فيُطالَب قبل أن تُغلق الأبواب لا بعدها */
    @Test
    void theSurveySaysHowManyDaysAreLeftBeforeTheDoorsClose() {
        LocalDate soon = NOW.toLocalDate().plusDays(3);

        CentreOperations row = CentreOperations.of(
                tenant(TenantStatus.ACTIVE, soon), upToDate(), 0, NOW);

        assertThat(row.paidThrough()).isEqualTo(soon);
        assertThat(row.daysRemaining()).isEqualTo(3);
        assertThat(row.lapsed()).isFalse();
        assertThat(row.needsAttention())
                .as("وكلُّ شيءٍ آخر في موعده - المطالبةُ وحدها هي ما يستدعي النظر")
                .isTrue();
    }

    /** وسنترٌ بلا اشتراكٍ يُتابَع لا يُطالَب ولا يُغلق */
    @Test
    void aCentreWithNoTrackedSubscriptionIsNeitherChasedNorClosed() {
        CentreOperations row = CentreOperations.of(
                tenant(TenantStatus.ACTIVE, null), upToDate(), 0, NOW);

        assertThat(row.paidThrough()).isNull();
        assertThat(row.lapsed()).isFalse();
        assertThat(row.needsAttention()).isFalse();
    }

    /**
     * والاشتراكُ يُقال حتى عن سنترٍ تعذّرت قراءةُ قاعدته.
     *
     * <p>هو في سجلّ المنصة لا في قاعدة السنتر، فتعذُّرُ فتحها لا يُخفي متى دفع - وهو
     * أولُ ما يُسأل عنه حين يصمت سنتر.</p>
     */
    @Test
    void anUnreadableCentreStillSaysWhenItPaid() {
        LocalDate paidThrough = NOW.toLocalDate().plusDays(20);

        CentreOperations row = CentreOperations.unreadable(
                tenant(TenantStatus.ACTIVE, paidThrough), "قاعدة هذا السنتر مقطوعة");

        assertThat(row.paidThrough()).isEqualTo(paidThrough);
    }

    private static CentreOperations of(TenantStatus status, CenterSettings settings, long critical) {
        return CentreOperations.of(tenant(status), settings, critical, NOW);
    }

    private static PlatformTenant tenant(TenantStatus status) {
        return tenant(status, PAID_THROUGH);
    }

    private static PlatformTenant tenant(TenantStatus status, LocalDate paidThrough) {
        return new PlatformTenant(new TenantId(7), "سنتر النور", "noor",
                SchemaName.of("center_noor"), status, paidThrough);
    }

    private static CenterSettings dailyBackupAt(LocalTime hour, LocalDateTime lastRun) {
        return CenterSettings.builder()
                .autoBackupEnabled(true)
                .backupFrequency(BackupFrequency.DAILY)
                .backupTime(hour)
                .lastAutoBackupAt(lastRun)
                .build();
    }

    /** سنترٌ كلُّ مهامّه في موعدها: نُسخ قبل قليل وفُحص قبل قليل */
    private static CenterSettings upToDate() {
        CenterSettings settings = dailyBackupAt(LocalTime.of(2, 0), NOW.minusHours(1));
        settings.setAlertsEnabled(true);
        settings.setAlertScanTime(LocalTime.of(8, 0));
        settings.setLastAlertScanAt(NOW.minusHours(1));
        return settings;
    }
}
