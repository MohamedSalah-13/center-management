package com.codejava.center.core.group;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * هل يلتقي موعدا مجموعتين.
 *
 * <p>هذا هو القرار الذي يمنع المعلم من أن يكون في قاعتين في وقت واحد، وقرارٌ كهذا يجب
 * أن يُختبَر بلا قاعدة بيانات ولا سياق تطبيق — فمكانه النواة. أما الكيانات والأسماء
 * المشتقّة فعلى جهة التطبيق في {@code service/GroupSchedules}: تلك تعرف
 * {@code CourseGroup} وتعرف لغةَ من يقرأ.</p>
 *
 * <p><b>المقارنة لا تنظر إلى المعلم:</b> من يستدعيها هو من يحصر المقارنة في مجموعات
 * معلم واحد، لأن مجموعتين لمعلمين مختلفين في قاعتين مختلفتين وقتٌ واحد أمر عادي — ومنعه
 * يمنع نصف جدول السنتر.</p>
 */
public final class GroupSchedule {

    /** أطول اسم يقبله عمود {@code course_groups.name} */
    public static final int MAX_NAME_LENGTH = 100;

    private GroupSchedule() {
    }

    /** موعدٌ مكتمل: أيامٌ وبدايةٌ ونهاية. ما نقص منه لا يُحكم عليه بتعارض ولا بسلامة */
    public static boolean isComplete(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
        return days != null && !days.isEmpty() && start != null && end != null;
    }

    /** هل يلتقي الموعدان في يوم واحد على الأقل وفي وقت متداخل */
    public static boolean conflicts(Set<DayOfWeek> firstDays, LocalTime firstStart, LocalTime firstEnd,
                                    Set<DayOfWeek> secondDays, LocalTime secondStart, LocalTime secondEnd) {
        if (!isComplete(firstDays, firstStart, firstEnd)
                || !isComplete(secondDays, secondStart, secondEnd)) {
            return false;
        }
        return firstDays.stream().anyMatch(secondDays::contains)
                && timesOverlap(firstStart, firstEnd, secondStart, secondEnd);
    }

    /**
     * تداخل فترتين زمنيتين.
     *
     * <p>التلامس ليس تداخلاً: مجموعة تنتهي الرابعة وأخرى تبدأ الرابعة موعدان صحيحان،
     * وهو الترتيب الشائع في السنتر. ولذلك المقارنة {@code <} لا {@code <=}.</p>
     */
    public static boolean timesOverlap(LocalTime firstStart, LocalTime firstEnd,
                                       LocalTime secondStart, LocalTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
