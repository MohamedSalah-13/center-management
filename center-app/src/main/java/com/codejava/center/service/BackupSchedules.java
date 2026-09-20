package com.codejava.center.service;

import com.codejava.center.core.backup.BackupFrequency;
import com.codejava.center.core.backup.BackupSchedule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.util.I18n;

import java.time.DayOfWeek;
import java.time.format.TextStyle;

/**
 * ما يحتاجه {@link BackupSchedule} من هذا التطبيق: صفُّ الإعدادات، وحزمةُ النصوص.
 *
 * <p>الحساب نفسه - متى الموعد التالي، وهل فات - في النواة حيث لا Spring ولا JPA ولا
 * لغة. وهنا ما لا يصحّ أن يكون هناك: قراءةُ كيان JPA، وجملةٌ تُكتب بلغة من يقرؤها.</p>
 */
public final class BackupSchedules {

    private BackupSchedules() {
    }

    public static BackupSchedule from(CenterSettings settings) {
        return BackupSchedule.of(
                settings.getBackupFrequency(),
                settings.getBackupTime(),
                settings.getBackupDayOfWeek(),
                settings.getBackupDayOfMonth());
    }

    /** وصف الموعد بلغة الواجهة، لعرضه في شاشة الإعدادات */
    public static String describe(BackupSchedule schedule) {
        return switch (schedule.frequency()) {
            case DAILY -> I18n.format("settings.scheduleDaily", schedule.clock());
            case WEEKLY -> I18n.format("settings.scheduleWeekly",
                    dayOfWeekName(schedule.dayOfWeek()), schedule.clock());
            case MONTHLY -> I18n.format("settings.scheduleMonthly",
                    schedule.dayOfMonth(), schedule.clock());
        };
    }

    /**
     * اسم التكرار بلغة الواجهة.
     *
     * <p>هنا لا {@code getDisplayName()} على الثابت نفسه كبقية الـ enums: {@link
     * BackupFrequency} في النواة، والنواة لا تعرف حزمة نصوص. والمفاتيح مضمونة بحلقة
     * {@code backupFrequency.*} في {@code MessageBundleTest}.</p>
     */
    public static String frequencyName(BackupFrequency frequency) {
        return frequency == null ? "" : I18n.get("backupFrequency." + frequency.name());
    }

    /** اسم يوم الأسبوع بلغة الواجهة، مصدره {@link DayOfWeek} لا قائمة مكتوبة في الكود */
    public static String dayOfWeekName(int value) {
        return DayOfWeek.of(value).getDisplayName(TextStyle.FULL, I18n.current());
    }
}
