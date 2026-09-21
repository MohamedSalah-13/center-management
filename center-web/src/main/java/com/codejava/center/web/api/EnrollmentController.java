package com.codejava.center.web.api;

import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.dto.MembershipRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * اشتراكات الطالب: مَوردٌ تحت الطالب، لا موردٌ قائم بذاته.
 *
 * <p>{@code /api/students/{id}/enrollments} لأن الاشتراك لا معنى له بلا صاحبه: مسارٌ
 * مسطَّح {@code /api/enrollments/{id}} يجعل الطالبَ حقلاً في الجسم - أي شيئاً يختاره
 * المُرسِل - بينما هو هنا في المسار الذي رآه سجلُّ الخادم.</p>
 *
 * <p>ولا قرار هنا: السعةُ تحت قفل، وقيدُ الصف، وإعادةُ تفعيل عضوية سابقة بدل صفٍّ
 * مكرر، وسباقُ جهازين - كلُّها في {@code EnrollmentService} برسائلها المترجمة.</p>
 *
 * <p>والإنهاء {@code DELETE} في الرابط وليس حذفاً في القاعدة: الصفُّ يبقى بتاريخ خروج،
 * وهو ما يحدّ مدةَ حساب حضور الطالب وجوابُ "منذ متى لم يعد يأتي؟". حذفُه يجعل حصصه
 * تُحسب على عمر المجموعة كله.</p>
 */
@RestController
@RequestMapping("/api/students/{studentId}/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public record EnrollRequest(@NotNull Long groupId) {
    }

    /**
     * @param sessionsHeld    حصص المجموعة <b>داخل مدة اشتراك هذا الطالب</b> لا عمرها كله
     * @param attendanceRate  نسبة الحضور، أو {@code null} حين لا تنعقد حصة بعد
     */
    public record MembershipView(Long groupId, String groupName, LocalDate joinDate,
                                 LocalDate leaveDate, boolean active,
                                 long sessionsHeld, long sessionsAttended,
                                 Integer attendanceRate) {
    }

    /** السارية والمنتهية معاً: المنتهية هي تاريخ الطالب، وإخفاؤها يجعل أرقامه غير مفهومة */
    @GetMapping
    public List<MembershipView> memberships(@PathVariable Long studentId) {
        return enrollmentService.getMemberships(studentId).stream()
                .map(EnrollmentController::view)
                .toList();
    }

    @PostMapping
    public List<MembershipView> enrol(@PathVariable Long studentId,
                                      @Valid @RequestBody EnrollRequest request) {
        enrollmentService.subscribe(studentId, request.groupId());
        return memberships(studentId);
    }

    @DeleteMapping("/{groupId}")
    public List<MembershipView> end(@PathVariable Long studentId, @PathVariable Long groupId) {
        enrollmentService.unsubscribe(studentId, groupId);
        return memberships(studentId);
    }

    private static MembershipView view(MembershipRow row) {
        return new MembershipView(row.groupId(), row.groupName(), row.joinDate(), row.leaveDate(),
                row.active(), row.sessionsHeld(), row.sessionsAttended(), row.attendanceRate());
    }
}
