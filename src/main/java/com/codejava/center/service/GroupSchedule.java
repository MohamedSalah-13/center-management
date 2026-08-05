package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.util.I18n;
import com.codejava.center.util.WeekDays;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * موعد المجموعة: هل يتعارض مع موعد آخر، وما اسمها المشتق منه.
 *
 * <p>صنف ساكن خالٍ من Spring و JPA على غرار {@link BackupSchedule}: هذا هو القرار
 * الذي يمنع المعلم من أن يكون في قاعتين في وقت واحد، وقرار كهذا يجب أن يُختبَر بلا
 * قاعدة بيانات ولا سياق تطبيق.</p>
 */
public final class GroupSchedule {

    /** أطول اسم يقبله عمود {@code course_groups.name} */
    public static final int MAX_NAME_LENGTH = 100;

    private GroupSchedule() {
    }

    /**
     * هل تلتقي المجموعتان في يوم واحد على الأقل وفي وقت متداخل؟
     *
     * <p>المقارنة لا تنظر إلى المعلم: من يستدعيها هو من يحصر المقارنة في مجموعات معلم
     * واحد، لأن مجموعتين لمعلمين مختلفين في قاعتين مختلفتين وقتٌ واحد أمر عادي.</p>
     */
    public static boolean conflicts(CourseGroup first, CourseGroup second) {
        if (first == null || second == null) {
            return false;
        }
        // مجموعة بلا موعد (من قبل هذه الميزة) لا يمكن الحكم عليها بتعارض ولا بسلامة
        if (!hasSchedule(first) || !hasSchedule(second)) {
            return false;
        }
        return !sharedDays(first, second).isEmpty()
                && timesOverlap(first.getStartTime(), first.getEndTime(),
                                second.getStartTime(), second.getEndTime());
    }

    public static boolean hasSchedule(CourseGroup group) {
        return group.getMeetingDays() != null && !group.getMeetingDays().isEmpty()
                && group.getStartTime() != null && group.getEndTime() != null;
    }

    /** الأيام المشتركة بين موعدين، بترتيب الأسبوع - تُعرض في رسالة التعارض */
    public static Set<DayOfWeek> sharedDays(CourseGroup first, CourseGroup second) {
        Set<DayOfWeek> shared = new LinkedHashSet<>();
        if (first.getMeetingDays() == null || second.getMeetingDays() == null) {
            return shared;
        }
        first.getMeetingDays().stream()
                .filter(second.getMeetingDays()::contains)
                .sorted(WeekDays.weekOrder())
                .forEach(shared::add);
        return shared;
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

    /**
     * الاسم المشتق: الصف ثم المعلم ثم الأيام ثم الساعة.
     *
     * <p>يُخزَّن مبنياً لا محسوباً عند العرض، لأن اسم المجموعة يظهر في الإيصالات وسجل
     * المراقبة وكشوف الحساب، وتلك سجلات تصف ما كان لا ما هو كائن الآن.</p>
     */
    public static String compose(SchoolLevel level, String teacherName,
                                 Set<DayOfWeek> days, LocalTime startTime) {
        String name = I18n.format("group.autoName",
                level == null ? I18n.get("common.none") : level.getDisplayName(),
                teacherName == null ? I18n.get("common.none") : teacherName,
                WeekDays.describe(days),
                WeekDays.describeTime(startTime));

        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    public static String compose(CourseGroup group) {
        return compose(group.getSchoolLevel(),
                group.getTeacher() == null ? null : group.getTeacher().getName(),
                group.getMeetingDays(), group.getStartTime());
    }
}
