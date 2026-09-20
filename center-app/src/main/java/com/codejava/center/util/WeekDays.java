package com.codejava.center.util;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * أيام الأسبوع والتوقيت كما يقرؤهما مستخدم السنتر.
 *
 * <p>الأسبوع يبدأ بالسبت لا بالإثنين: {@link DayOfWeek} يرتّب أيامه بترتيب ISO، وعرض
 * مواعيد سنتر مصري بذلك الترتيب يضع يومَي الإجازة في وسط القائمة.</p>
 *
 * <p>أسماء الأيام تأتي من الـ JDK لا من حزمة الرسائل: هي موجودة في CLDR بكل اللغات
 * المدعومة، ونسخها إلى {@code messages.properties} يعني صيانة سبعة مفاتيح لكل لغة
 * مقابل لا شيء.</p>
 */
public final class WeekDays {

    /** ترتيب العرض: السبت أول أيام الأسبوع الدراسي في مصر */
    public static final List<DayOfWeek> ORDER = List.of(
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static final Comparator<DayOfWeek> BY_WEEK_ORDER = Comparator.comparingInt(ORDER::indexOf);

    private WeekDays() {
    }

    public static String displayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, I18n.current());
    }

    /** أيام المجموعة في سطر واحد بترتيب الأسبوع: "السبت، الثلاثاء" */
    public static String describe(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return I18n.get("common.none");
        }
        return days.stream()
                .sorted(BY_WEEK_ORDER)
                .map(WeekDays::displayName)
                .collect(Collectors.joining(I18n.get("common.listSeparator") + " "));
    }

    public static Comparator<DayOfWeek> weekOrder() {
        return BY_WEEK_ORDER;
    }

    /**
     * الساعة بصيغة 12 ساعة بلغة الواجهة، لأن مواعيد السنتر تُقال "أربعة العصر"
     * لا "16:00". {@code null} تعني موعداً غير مضبوط بعد.
     */
    public static String describeTime(LocalTime time) {
        return time == null
                ? I18n.get("common.none")
                : time.format(DateTimeFormatter.ofPattern("hh:mm a", I18n.current()));
    }

    /** الفترة كاملة: "04:00 م - 06:00 م" */
    public static String describeRange(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return I18n.get("common.none");
        }
        return I18n.format("group.timeRange", describeTime(start), describeTime(end));
    }
}
