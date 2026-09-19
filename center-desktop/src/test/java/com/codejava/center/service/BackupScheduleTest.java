package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.BackupFrequency;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حساب موعد النسخة الاحتياطية القادمة.
 *
 * <p>الخطأ هنا لا يظهر في أي شاشة: البرنامج يعمل، والإعداد يبدو مفعَّلاً، ولا تُؤخذ نسخة -
 * أو تُؤخذ في موعد غير الذي اختاره السنتر. ولا يُكتشف ذلك إلا يوم تُطلب النسخة.</p>
 *
 * <p>لا سياق Spring ولا JavaFX ولا قاعدة بيانات: {@link BackupSchedule} صنف حساب خالص،
 * لنفس سبب كون {@code Printing.pageBreaks} كذلك.</p>
 */
class BackupScheduleTest {

    private static final LocalTime AT_TWO = LocalTime.of(2, 0);

    @Test
    void dailyRunsLaterTheSameDayWhenTheTimeHasNotPassed() {
        BackupSchedule schedule = daily(AT_TWO);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 4, 1, 30));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 4, 2, 0));
    }

    @Test
    void dailyMovesToTomorrowWhenTheTimeHasPassed() {
        BackupSchedule schedule = daily(AT_TWO);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 4, 9, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 5, 2, 0));
    }

    /** المساواة تعني الدوران في حلقة: المشغّل يسأل عن التالي بعد كل تنفيذ */
    @Test
    void nextRunIsStrictlyAfterTheReferenceMoment() {
        BackupSchedule schedule = daily(AT_TWO);
        LocalDateTime exactly = LocalDateTime.of(2026, 8, 4, 2, 0);

        assertThat(schedule.nextRunAfter(exactly)).isEqualTo(exactly.plusDays(1));
    }

    @Test
    void weeklyPicksTheChosenDayInTheSameWeek() {
        BackupSchedule schedule = new BackupSchedule(
                BackupFrequency.WEEKLY, AT_TWO, DayOfWeek.FRIDAY.getValue(), 1);

        // 2026-08-04 يوم ثلاثاء
        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 4, 12, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 7, 2, 0));
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    void weeklySkipsToNextWeekWhenTodayIsTheDayAndTheTimeHasPassed() {
        BackupSchedule schedule = new BackupSchedule(
                BackupFrequency.WEEKLY, AT_TWO, DayOfWeek.FRIDAY.getValue(), 1);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 7, 10, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 14, 2, 0));
    }

    @Test
    void monthlyPicksTheChosenDayInTheSameMonth() {
        BackupSchedule schedule = new BackupSchedule(BackupFrequency.MONTHLY, AT_TWO, 1, 15);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 4, 12, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 8, 15, 2, 0));
    }

    @Test
    void monthlyMovesToNextMonthWhenTheDayHasPassed() {
        BackupSchedule schedule = new BackupSchedule(BackupFrequency.MONTHLY, AT_TWO, 1, 15);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 8, 20, 12, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 9, 15, 2, 0));
    }

    /** اليوم 31 لا وجود له في فبراير: القصر على آخر يوم أقرب لمراد المستخدم من تخطّي الشهر */
    @Test
    void monthlyFallsBackToTheLastDayOfShorterMonths() {
        BackupSchedule schedule = new BackupSchedule(BackupFrequency.MONTHLY, AT_TWO, 1, 31);

        LocalDateTime next = schedule.nextRunAfter(LocalDateTime.of(2026, 2, 1, 12, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 28, 2, 0));
    }

    @Test
    void aScheduleThatHasNeverRunIsOverdue() {
        assertThat(daily(AT_TWO).isOverdue(null, LocalDateTime.of(2026, 8, 4, 9, 0))).isTrue();
    }

    /** الحالة التي وُجد التعويض من أجلها: الجهاز كان مطفأً الساعة الثانية فجراً */
    @Test
    void aMissedNightIsOverdue() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 8, 2, 2, 0);

        assertThat(daily(AT_TWO).isOverdue(lastRun, LocalDateTime.of(2026, 8, 4, 9, 0))).isTrue();
    }

    @Test
    void aScheduleWhoseNextSlotHasNotArrivedIsNotOverdue() {
        LocalDateTime lastRun = LocalDateTime.of(2026, 8, 4, 2, 0);

        assertThat(daily(AT_TWO).isOverdue(lastRun, LocalDateTime.of(2026, 8, 4, 9, 0))).isFalse();
    }

    /**
     * قاعدة بيانات رُقّيت من نسخة أقدم لا تحمل أياً من أعمدة الجدولة، ويجب أن تستمر
     * على الموعد الذي كان مكتوباً في الكود قبل الترقية.
     */
    @Test
    void settingsWithoutAScheduleKeepTheHistoricDailyTwoAmSlot() {
        BackupSchedule schedule = BackupSchedule.from(CenterSettings.builder().build());

        assertThat(schedule.frequency()).isEqualTo(BackupFrequency.DAILY);
        assertThat(schedule.time()).isEqualTo(LocalTime.of(2, 0));
    }

    @Test
    void outOfRangeDaysAreClampedInsteadOfThrowing() {
        CenterSettings settings = CenterSettings.builder()
                .backupFrequency(BackupFrequency.WEEKLY)
                .backupDayOfWeek(99)
                .backupDayOfMonth(0)
                .build();

        BackupSchedule schedule = BackupSchedule.from(settings);

        assertThat(schedule.dayOfWeek()).isEqualTo(7);
        assertThat(schedule.dayOfMonth()).isEqualTo(1);
    }

    private BackupSchedule daily(LocalTime time) {
        return new BackupSchedule(BackupFrequency.DAILY, time, 1, 1);
    }
}
