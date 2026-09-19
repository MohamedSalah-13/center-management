package com.codejava.center.util;

import java.time.Duration;

/**
 * صياغة مدة المكوث نصاً: "1 س 20 د".
 *
 * <p>مكتوبة مرة واحدة لأن ثلاثة مواضع تعرضها: شاشة الحضور، وشاشة الكشف، وورقة جاسبر.
 * وحساب الساعات والدقائق مكرَّراً ثلاث مرات ينتهي باختلافها في التقريب، فيقرأ الموظف
 * رقماً على الشاشة وآخر على الورقة عن الطالب نفسه.</p>
 *
 * <p>خالية من JavaFX عمداً - كـ {@code Printing.pageBreaks} و{@code BackupSchedule} -
 * فتُختبر بلا مجموعة أدوات رسومية، ولا واحدة على خادم البناء.</p>
 */
public final class Durations {

    private Durations() {
    }

    /**
     * @param duration المدة، أو {@code null} إن كانت غير معلومة
     * @return نصّ المدة، أو {@code common.none} إن كانت {@code null} أو سالبة
     */
    public static String format(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return I18n.get("common.none");
        }

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        // الساعات تُذكر فقط حين توجد: "0 س 5 د" تُقرأ أطول مما هي
        return hours > 0
                ? I18n.format("common.duration.hoursMinutes", hours, minutes)
                : I18n.format("common.duration.minutes", minutes);
    }
}
