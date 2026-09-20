package com.codejava.center.core.backup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * موعد النسخة الاحتياطية التلقائية القادمة.
 *
 * <p>صنف خالٍ من Spring ومن JavaFX عن قصد، تماماً كـ {@code Printing.pageBreaks}: هذا هو
 * الحساب الذي إن أخطأ لم تُؤخذ نسخة لأشهر دون أن ينتبه أحد، و{@code BackupScheduleTest}
 * يغطّيه بلا سياق تطبيق ولا قاعدة بيانات.</p>
 *
 * <p>القيم الناقصة تُملأ بافتراضات معقولة بدل رفضها: قاعدة بيانات رُقّيت من نسخة أقدم لا
 * تحمل أياً من الأعمدة الجديدة، ويجب أن تستمر في أخذ نسختها اليومية الثانية صباحاً كما
 * كانت قبل الترقية. ولذلك يأخذ {@link #of} قيماً تقبل {@code null}.</p>
 *
 * <p>ولا وصفَ نصّياً هنا: "يومياً الساعة 02:00" جملةٌ بلغة من يقرؤها، والنواة لا تعرف
 * حزمة نصوص. راجع {@code service/BackupSchedules} على جهة التطبيق - هناك تُقرأ إعدادات
 * السنتر وتُكتب الجملة.</p>
 *
 * @param dayOfWeek  يوم الأسبوع للتكرار الأسبوعي (1 = الاثنين … 7 = الأحد)
 * @param dayOfMonth يوم الشهر للتكرار الشهري؛ يُقصَر على آخر يوم في الأشهر الأقصر
 */
public record BackupSchedule(BackupFrequency frequency, LocalTime time, int dayOfWeek, int dayOfMonth) {

    /** موعد النسخ في كل النسخ السابقة من البرنامج، فلا يتغيّر سلوك من لم يفتح الإعدادات */
    public static final LocalTime DEFAULT_TIME = LocalTime.of(2, 0);

    public static final BackupFrequency DEFAULT_FREQUENCY = BackupFrequency.DAILY;

    /** الاثنين: أول أيام الأسبوع في {@link DayOfWeek}، وقيمة صالحة أياً كان عرف السنتر */
    public static final int DEFAULT_DAY_OF_WEEK = DayOfWeek.MONDAY.getValue();

    public static final int DEFAULT_DAY_OF_MONTH = 1;

    /**
     * موعد من قيم قد تكون ناقصة أو خارج المدى.
     *
     * <p>القصر لا الرفض: يوم 40 من الشهر في صفٍّ قديم لا يصحّ أن يعني "لا نسخة أبداً".</p>
     */
    public static BackupSchedule of(BackupFrequency frequency, LocalTime time,
                                    Integer dayOfWeek, Integer dayOfMonth) {
        return new BackupSchedule(
                orDefault(frequency, DEFAULT_FREQUENCY),
                orDefault(time, DEFAULT_TIME),
                clamp(dayOfWeek, 1, 7, DEFAULT_DAY_OF_WEEK),
                clamp(dayOfMonth, 1, 31, DEFAULT_DAY_OF_MONTH));
    }

    /**
     * أول موعد تشغيل يقع <b>بعد</b> اللحظة المعطاة تماماً.
     *
     * <p>الاشتراط "بعد" لا "بعد أو يساوي" مقصود: الدالة تُستدعى بعد كل تنفيذ لتحديد
     * التالي، ولو قبلت المساواة لأعادت اللحظة نفسها ودارت المهمة في حلقة.</p>
     */
    public LocalDateTime nextRunAfter(LocalDateTime reference) {
        return switch (frequency) {
            case DAILY -> firstAfter(reference, reference.toLocalDate(), 1);
            case WEEKLY -> {
                LocalDate candidate = reference.toLocalDate();
                // فرق الأيام حتى اليوم المطلوب من الأسبوع، وقد يكون اليوم نفسه
                int shift = Math.floorMod(dayOfWeek - candidate.getDayOfWeek().getValue(), 7);
                yield firstAfter(reference, candidate.plusDays(shift), 7);
            }
            case MONTHLY -> {
                LocalDateTime thisMonth = atDayOfMonth(reference.toLocalDate());
                yield thisMonth.isAfter(reference)
                        ? thisMonth
                        : atDayOfMonth(reference.toLocalDate().withDayOfMonth(1).plusMonths(1));
            }
        };
    }

    /**
     * هل فات موعد كان يجب أن تُؤخذ فيه نسخة.
     *
     * <p>الجهاز في السنتر يُطفأ ليلاً، والموعد الافتراضي الثانية فجراً: بلا هذا التعويض
     * يمكن ألا تُؤخذ ولا نسخة واحدة طوال العام والإعداد يبدو مفعَّلاً.</p>
     *
     * @param lastRun آخر نسخة تلقائية ناجحة، أو {@code null} إن لم تُؤخذ أي نسخة بعد
     */
    public boolean isOverdue(LocalDateTime lastRun, LocalDateTime now) {
        return lastRun == null || !nextRunAfter(lastRun).isAfter(now);
    }

    /** الساعة والدقيقة بصيغة 24، وهي جزء من كل وصف للموعد */
    public String clock() {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /**
     * أول موعد بعد المرجع، ابتداءً من التاريخ المرشَّح وبالقفز بطول الدورة إن كان قد فات.
     * القفزة الواحدة تكفي دائماً لأن المرشَّح لا يسبق يوم المرجع.
     */
    private LocalDateTime firstAfter(LocalDateTime reference, LocalDate candidate, int periodDays) {
        LocalDateTime at = candidate.atTime(time);
        return at.isAfter(reference) ? at : candidate.plusDays(periodDays).atTime(time);
    }

    /**
     * اليوم المطلوب من شهر التاريخ المعطى. يوم 31 في فبراير لا وجود له، والقصر على آخر
     * يوم في الشهر أقرب إلى ما يقصده المستخدم من تخطّي الشهر بلا نسخة.
     */
    private LocalDateTime atDayOfMonth(LocalDate within) {
        return within.withDayOfMonth(Math.min(dayOfMonth, within.lengthOfMonth())).atTime(time);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }
}
