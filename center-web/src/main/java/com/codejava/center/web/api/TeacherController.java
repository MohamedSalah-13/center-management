package com.codejava.center.web.api;

import com.codejava.center.domain.Teacher;
import com.codejava.center.service.TeacherService;
import com.codejava.center.service.dto.TeacherDraft;
import com.codejava.center.util.CommissionTypes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import java.util.List;

/**
 * المعلمون: قراءةً وكتابةً، بعد {@code TeacherDraft}.
 *
 * <p>وصلوا قراءةً مع كتابة المجموعات لأنهم شرطُها - من ينشئ مجموعةً يختار معلمها أولاً -
 * وكان الإنشاء مؤجَّلاً حتى تسبقه مسودة، كما أُجِّلت شاشةُ المجموعات إلى ما بعد
 * {@code CourseGroupDraft}.</p>
 *
 * <p>وكلُّه {@code @RequiresRole(ADMIN)} في الخدمة: صفُّ المعلم يحمل نوعَ عمولته
 * وقيمتها - أي ما يتقاضاه - وشاشةُ المجموعات نفسها لا تُفتح إلا للمدير.</p>
 *
 * <p><b>والعمولة تُسلَّم في القائمة الكاملة ولا تُسلَّم في القائمة المختصرة.</b>
 * {@code GET /api/teachers} يخدم نموذجَ المجموعة ولا يحتاج إلا الاسم والمادة، فإرسالُ
 * الصفّ كما هو يضع ما يتقاضاه كلُّ معلم في جوابٍ لم يطلبه أحد - ومن يريدها يطلب
 * {@code ?withCommission=true}، وهو ما تفعله شاشةُ المعلمين وحدها.</p>
 */
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /** العمولةُ ثلاثةُ حقول أو لا شيء: نوعُها خاماً، واسمُه المترجَم، وقيمتها */
    public record TeacherView(Long id, String name, Long subjectId, String subject,
                              String commissionType, String commissionName,
                              BigDecimal commissionValue) {
    }

    public record TeacherRequest(
            @NotBlank @Size(max = 100) String name,
            @jakarta.validation.constraints.NotNull Long subjectId,
            @Size(max = 20) String commissionType,
            BigDecimal commissionValue) {

        TeacherDraft toDraft(Long id) {
            return new TeacherDraft(id, name, subjectId, commissionType, commissionValue);
        }
    }

    public record CommissionTypeView(String name, String label) {
    }

    /**
     * @param withCommission ما تحتاجه شاشةُ المعلمين وحدها - وهي التي تعرضها أصلاً
     */
    @GetMapping
    public List<TeacherView> teachers(
            @RequestParam(defaultValue = "false") boolean withCommission,
            @RequestParam(required = false) Long subjectId) {
        return teacherService.getAllTeachers().stream()
                .filter(teacher -> subjectId == null || subjectId.equals(teacher.getSubjectDefinition().getId()))
                .map(teacher -> view(teacher, withCommission))
                .toList();
    }

    /** أنواعُ العمولة التي يعرفها الحساب - لا قائمةٌ مكتوبةٌ في الصفحة تدرج نوعاً يُرفض */
    @GetMapping("/commission-types")
    public List<CommissionTypeView> commissionTypes() {
        return CommissionTypes.KNOWN.stream()
                .map(type -> new CommissionTypeView(type, CommissionTypes.displayName(type)))
                .toList();
    }

    @PostMapping
    public TeacherView create(@Valid @RequestBody TeacherRequest request) {
        return view(teacherService.saveTeacher(request.toDraft(null)), true);
    }

    @PutMapping("/{id}")
    public TeacherView update(@PathVariable Long id, @Valid @RequestBody TeacherRequest request) {
        return view(teacherService.saveTeacher(request.toDraft(id)), true);
    }

    /**
     * الحذف، وهو ممنوعٌ على من له مجموعة.
     *
     * <p>المفتاحُ الأجنبي على {@code course_groups} يردّه، ويصل {@code 409} بجملةٍ
     * مترجَمة تقول إن له مجموعات - لا برسالةٍ فيها اسمُ القيد.</p>
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
    }

    private static TeacherView view(Teacher teacher, boolean withCommission) {
        return new TeacherView(teacher.getId(), teacher.getName(), teacher.getSubjectDefinition().getId(), teacher.getSubject(),
                withCommission ? teacher.getCommissionType() : null,
                withCommission ? CommissionTypes.displayName(teacher.getCommissionType()) : null,
                withCommission ? teacher.getCommissionValue() : null);
    }
}
