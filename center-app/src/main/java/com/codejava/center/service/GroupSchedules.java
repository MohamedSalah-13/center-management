package com.codejava.center.service;

import com.codejava.center.core.group.GroupSchedule;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.util.I18n;
import com.codejava.center.util.WeekDays;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ما يحتاجه {@link GroupSchedule} من هذا التطبيق: كياناتُ JPA، واسمٌ يُقرأ بلغة.
 *
 * <p>القرار - هل يلتقي موعدان - في النواة. وهنا ما لا يصحّ أن يكون هناك: قراءةُ
 * {@code CourseGroup}، وترتيبُ الأيام بعرف السنتر (السبت أولاً)، والاسمُ المشتقّ الذي
 * يُكتب بحزمة النصوص.</p>
 */
public final class GroupSchedules {

    private GroupSchedules() {
    }

    /** هل تتعارض المجموعتان. الحصر في معلم واحد مسؤولية المستدعي */
    public static boolean conflicts(CourseGroup first, CourseGroup second) {
        if (first == null || second == null) {
            return false;
        }
        return GroupSchedule.conflicts(
                first.getMeetingDays(), first.getStartTime(), first.getEndTime(),
                second.getMeetingDays(), second.getStartTime(), second.getEndTime());
    }

    public static boolean hasSchedule(CourseGroup group) {
        return GroupSchedule.isComplete(group.getMeetingDays(),
                group.getStartTime(), group.getEndTime());
    }

    /**
     * الأيام المشتركة بين موعدين، بترتيب أسبوع السنتر - تُعرض في رسالة التعارض.
     *
     * <p>هنا لا في النواة لأنها للعرض لا للقرار: ترتيبُ "السبت أولاً" عرفٌ مصري في
     * {@link WeekDays}، والنواة لا تعرف أيَّ يوم يبدأ به أسبوع من يقرأ.</p>
     */
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

        return name.length() <= GroupSchedule.MAX_NAME_LENGTH
                ? name
                : name.substring(0, GroupSchedule.MAX_NAME_LENGTH);
    }

    public static String compose(CourseGroup group) {
        return compose(group.getSchoolLevel(),
                group.getTeacher() == null ? null : group.getTeacher().getName(),
                group.getMeetingDays(), group.getStartTime());
    }
}
