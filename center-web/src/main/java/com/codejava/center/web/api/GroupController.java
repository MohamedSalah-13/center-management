package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.WeekDays;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

/**
 * المجموعات - <b>قراءةً فقط، في هذه الدفعة.</b>
 *
 * <p>وصلت مع فتح الحصص لأنها شرطُه: من يفتح حصةً يختار مجموعةً أولاً، فقائمةٌ تُقرأ
 * ليست توسيعاً للنطاق بل نصفُ الميزة.</p>
 *
 * <p>أمّا الإنشاء والتعديل فيبقيان: {@code CourseGroupService.saveGroup} يأخذ
 * {@code CourseGroup} كما هو، و{@code CLAUDE.md} يقول <b>لا كيان JPA مدخلاً لـ HTTP
 * أبداً</b> - كيانٌ خلف رابطِ طلبٍ هو mass assignment ينتظر. فيسبق تلك الشاشةَ
 * {@code draft} كما سبق {@code UserDraft} شاشةَ المستخدمين، وذلك عملٌ في طبقة الأعمال
 * لا في الحافة، ومكانُه الدفعة التالية.</p>
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CourseGroupService courseGroupService;

    public record GroupView(Long id, String name, String teacherName, String level,
                            String meetingDays, LocalTime startTime, LocalTime endTime,
                            String sessionPrice) {
    }

    @GetMapping
    public List<GroupView> groups() {
        return courseGroupService.getAllGroups().stream()
                .map(GroupController::view)
                .toList();
    }

    /**
     * الأيام تُكتب بـ {@code WeekDays}، لا بأسماء {@code DayOfWeek} كما هي.
     *
     * <p>هو الذي يعرف أن الأسبوع يبدأ بالسبت في سنتر مصري، ويترجم الأسماء. وترتيبٌ
     * يبدأ بالإثنين - وهو ترتيب {@code DayOfWeek} - يقرؤه المستخدم على أنه خطأ.</p>
     */
    private static GroupView view(CourseGroup group) {
        return new GroupView(group.getId(), group.getName(),
                group.getTeacher() == null ? null : group.getTeacher().getName(),
                group.getSchoolLevel() == null ? null : group.getSchoolLevel().getDisplayName(),
                WeekDays.describe(group.getMeetingDays()),
                group.getStartTime(), group.getEndTime(),
                group.getSessionPrice() == null ? null
                        : MoneyUtils.formatWithCurrency(group.getSessionPrice()));
    }
}
