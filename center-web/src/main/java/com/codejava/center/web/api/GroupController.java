package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.dto.CourseGroupDraft;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.WeekDays;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * المجموعات: إنشاءً وتعديلاً وحذفاً، وكشفاً بمشتركيها.
 *
 * <p>وصلت قراءةً مع فتح الحصص لأنها شرطُه، وتكتمل هنا بعد {@code CourseGroupDraft}:
 * ما دام {@code saveGroup} يأخذ {@code CourseGroup} فلا إنشاءَ من الويب - <b>لا كيان
 * JPA مدخلاً لـ HTTP أبداً</b> - وكيانُ المعلم في جسم الطلب أوضحُ صورِ ذلك، إذ يحمل
 * اسمَه ونوعَ عمولته وقيمتها كما كتبها المُرسِل. المسودة تأخذ رقمه.</p>
 *
 * <p>والكتابةُ كلُّها {@code @RequiresRole(ADMIN)} في الخدمة، كما تُخفي الشاشةُ زرَّ
 * المجموعات عن السكرتير: من يضع جدول السنتر وأسعارَ حصصه هو صاحبه.</p>
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CourseGroupService courseGroupService;
    private final EnrollmentService enrollmentService;

    /** جسمُ الطلب بلا معرّف: المعرّف من المسار وحده - كما في {@code StudentController} */
    public record GroupRequest(
            @NotNull Long teacherId,
            @Size(max = 100) String name,
            boolean autoName,
            SchoolLevel schoolLevel,
            Integer maxCapacity,
            BigDecimal sessionPrice,
            Set<DayOfWeek> meetingDays,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {

        CourseGroupDraft toDraft(Long id) {
            return new CourseGroupDraft(id, teacherId, name, autoName, schoolLevel, maxCapacity,
                    sessionPrice, meetingDays, startTime, endTime);
        }
    }

    /**
     * الصفُّ مرتين: خاماً ليملأ النموذج، ومترجَماً ليُقرأ.
     *
     * <p>{@code level} و{@code meetingDays} هما ما يُعرض، و{@code levelName} و
     * {@code dayNames} هما ما يُعاد إرساله. وواحدٌ منهما لا يكفي: المترجَم لا يصلح
     * مدخلاً - يتغير بلغة القارئ - والثابتُ لا يصلح عرضاً.</p>
     */
    public record GroupView(Long id, String name, boolean autoName,
                            Long teacherId, String teacherName,
                            String levelName, String level,
                            List<String> dayNames, String meetingDays,
                            LocalTime startTime, LocalTime endTime,
                            Integer maxCapacity, BigDecimal sessionPrice, String price,
                            long members) {
    }

    /** اليوم الثابت واسمُه المترجَم، مرتَّبةً من السبت كما يقرؤها سنترٌ مصري */
    public record DayView(String name, String label) {
    }

    public record RosterRowView(Long studentId, String studentName, String barcode,
                                String phone, String parentPhone, LocalDate joinDate,
                                long sessionsHeld, long sessionsAttended, Integer attendanceRate) {
    }

    /**
     * @param level حصرُ القائمة على صفٍّ بعينه - وهو ما تحتاجه شاشة اشتراك الطالب
     */
    @GetMapping
    public List<GroupView> groups(@RequestParam(required = false) SchoolLevel level) {
        List<CourseGroup> groups = level == null
                ? courseGroupService.getAllGroups()
                : courseGroupService.getGroupsOfLevel(level);

        // عدّةٌ واحدة لكل القائمة: استعلامٌ لكل صفّ يجعل فتح الشاشة استعلاماً لكل مجموعة
        Map<Long, Long> members = enrollmentService.countActiveMembersPerGroup();
        return groups.stream()
                .map(group -> view(group, members.getOrDefault(group.getId(), 0L)))
                .toList();
    }

    /**
     * أيام الأسبوع بأسمائها المترجَمة ومرتَّبةً بترتيب {@code WeekDays}.
     *
     * <p>لا الصفحة تعرف أن الأسبوع يبدأ بالسبت، ولا هي تحمل نصاً عربياً. وترتيبٌ يبدأ
     * بالإثنين - وهو ترتيب {@code DayOfWeek} - يقرؤه المستخدم على أنه خطأ.</p>
     */
    @GetMapping("/days")
    public List<DayView> days() {
        return WeekDays.ORDER.stream()
                .map(day -> new DayView(day.name(), WeekDays.displayName(day)))
                .toList();
    }

    @GetMapping("/{id}")
    public GroupView one(@PathVariable Long id) {
        CourseGroup group = courseGroupService.findById(id);
        // عدّةُ هذه المجموعة وحدها: الخريطة الكاملة تُبنى للقائمة، وصفٌّ واحد لا يحتاجها
        return view(group, enrollmentService.countActiveMembers(group));
    }

    @PostMapping
    public GroupView create(@Valid @RequestBody GroupRequest request) {
        return view(courseGroupService.saveGroup(request.toDraft(null)), 0L);
    }

    @PutMapping("/{id}")
    public GroupView update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        CourseGroup saved = courseGroupService.saveGroup(request.toDraft(id));
        return view(saved, enrollmentService.countActiveMembers(saved));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        courseGroupService.deleteGroup(id);
    }

    /** كشفُ المجموعة: مشتركوها بحضور كلٍّ داخل مدة اشتراكه هو، لا داخل عمر المجموعة */
    @GetMapping("/{id}/roster")
    public List<RosterRowView> roster(@PathVariable Long id) {
        return enrollmentService.getRoster(id).stream()
                .map(GroupController::rosterRow)
                .toList();
    }

    /**
     * الأيام تُكتب بـ {@code WeekDays}، لا بأسماء {@code DayOfWeek} كما هي.
     *
     * <p>هو الذي يعرف أن الأسبوع يبدأ بالسبت في سنتر مصري، ويترجم الأسماء. وترتيبٌ
     * يبدأ بالإثنين - وهو ترتيب {@code DayOfWeek} - يقرؤه المستخدم على أنه خطأ.</p>
     */
    private static GroupView view(CourseGroup group, long members) {
        Set<DayOfWeek> days = group.getMeetingDays();
        return new GroupView(group.getId(), group.getName(), group.isAutoName(),
                group.getTeacher() == null ? null : group.getTeacher().getId(),
                group.getTeacher() == null ? null : group.getTeacher().getName(),
                group.getSchoolLevel() == null ? null : group.getSchoolLevel().name(),
                group.getSchoolLevel() == null ? null : group.getSchoolLevel().getDisplayName(),
                days == null ? List.of() : days.stream().sorted(WeekDays.weekOrder())
                        .map(DayOfWeek::name).toList(),
                WeekDays.describe(days),
                group.getStartTime(), group.getEndTime(),
                group.getMaxCapacity(), group.getSessionPrice(),
                group.getSessionPrice() == null ? null
                        : MoneyUtils.formatWithCurrency(group.getSessionPrice()),
                members);
    }

    private static RosterRowView rosterRow(MembershipRow row) {
        return new RosterRowView(row.studentId(), row.studentName(), row.barcode(),
                row.phone(), row.parentPhone(), row.joinDate(),
                row.sessionsHeld(), row.sessionsAttended(), row.attendanceRate());
    }
}
