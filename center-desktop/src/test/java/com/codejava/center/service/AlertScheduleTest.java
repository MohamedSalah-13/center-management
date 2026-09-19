package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.service.alert.AlertSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حساب موعد الفحص التالي.
 *
 * <p>بلا سياق تطبيق ولا قاعدة بيانات، تماماً كـ {@code BackupScheduleTest}: هذا هو
 * الحساب الذي إن أخطأ لم يظهر تنبيه واحد لأشهر بينما تبدو كل القواعد مفعَّلة في
 * الشاشة، وهو عطبٌ لا يكتشفه أحد لأن لا شيء يفشل.</p>
 */
class AlertScheduleTest {

    private static final AlertSchedule AT_EIGHT = new AlertSchedule(LocalTime.of(8, 0));

    @Test
    void nextRunIsTodayWhenTheTimeHasNotPassed() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 10, 6, 30);

        assertThat(AT_EIGHT.nextRunAfter(now))
                .isEqualTo(LocalDateTime.of(2026, 3, 10, 8, 0));
    }

    @Test
    void nextRunRollsToTomorrowWhenTheTimeHasPassed() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 10, 14, 0);

        assertThat(AT_EIGHT.nextRunAfter(now))
                .isEqualTo(LocalDateTime.of(2026, 3, 11, 8, 0));
    }

    /**
     * الاشتراط "بعد" لا "بعد أو يساوي": الدالة تُسأل بعد كل تنفيذ عن التالي، ولو قبلت
     * المساواة لأعادت اللحظة نفسها ودار الفحص في حلقة لا تنتهي.
     */
    @Test
    void nextRunNeverReturnsTheReferenceItself() {
        LocalDateTime exactlyOnTime = LocalDateTime.of(2026, 3, 10, 8, 0);

        assertThat(AT_EIGHT.nextRunAfter(exactlyOnTime))
                .isEqualTo(LocalDateTime.of(2026, 3, 11, 8, 0));
    }

    /**
     * أهمّ اختبار في الصف: جهاز السنتر قد يُفتح بعد موعد الفحص كل يوم، ولولا التعويض
     * لَما وقع فحص واحد طوال العام والنظام يبدو مفعَّلاً.
     */
    @Test
    void aScanIsOverdueWhenItsSlotPassedWhileTheMachineWasOff() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 3, 9, 8, 0);
        LocalDateTime nowAfterTodaysSlot = LocalDateTime.of(2026, 3, 10, 14, 0);

        assertThat(AT_EIGHT.isOverdue(lastRun, nowAfterTodaysSlot)).isTrue();
    }

    @Test
    void aScanIsNotOverdueBeforeItsNextSlot() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 3, 10, 8, 0);
        LocalDateTime laterSameDay = LocalDateTime.of(2026, 3, 10, 20, 0);

        assertThat(AT_EIGHT.isOverdue(lastRun, laterSameDay)).isFalse();
    }

    /** لم يقع فحص قطّ: مستحقّ فوراً، وإلا انتظر تركيبٌ جديد إلى الغد بلا سبب */
    @Test
    void neverScannedCountsAsOverdue() {
        assertThat(AT_EIGHT.isOverdue(null, LocalDateTime.of(2026, 3, 10, 9, 0))).isTrue();
    }

    /**
     * قاعدة بيانات رُقّيت من نسخة أقدم لا تحمل عمود الموعد، ويجب أن تعمل بالموعد
     * الافتراضي بدل أن ترمي {@code NullPointerException} في خيط المجدوِل حيث لا يراها أحد.
     */
    @Test
    void settingsWithoutAScanTimeFallBackToTheDefault() {
        AlertSchedule schedule = AlertSchedule.from(CenterSettings.builder().build());

        assertThat(schedule.time()).isEqualTo(AlertSchedule.DEFAULT_TIME);
    }

    @Test
    void settingsWithAScanTimeUseIt() {
        CenterSettings settings = CenterSettings.builder().alertScanTime(LocalTime.of(21, 30)).build();

        assertThat(AlertSchedule.from(settings).time()).isEqualTo(LocalTime.of(21, 30));
    }
}
