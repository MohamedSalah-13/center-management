package com.codejava.center.web.api;

import com.codejava.center.domain.Teacher;
import com.codejava.center.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * المعلمون - <b>قراءةً فقط، في هذه الدفعة.</b>
 *
 * <p>وصلوا مع كتابة المجموعات لأنهم شرطُها: من ينشئ مجموعةً يختار معلمها أولاً. أما
 * الإنشاء والتعديل فيبقيان حتى يسبقهما {@code TeacherDraft}، لأن
 * {@code TeacherService.saveTeacher} ما زال يأخذ {@code Teacher} كما هو - وهي القاعدة
 * نفسها التي أخّرت شاشة المجموعات إلى ما بعد {@code CourseGroupDraft}.</p>
 *
 * <p>والقائمة {@code @RequiresRole(ADMIN)} في الخدمة، ولم تُوسَّع من أجل هذه الحافة:
 * صفُّ المعلم يحمل نوع عمولته وقيمتها - أي ما يتقاضاه - وشاشةُ المجموعات نفسها لا
 * تُفتح إلا للمدير. فالحدّان متطابقان، والقراءةُ هنا لا تكشف أكثر مما تكشفه هناك.</p>
 *
 * <p>ولا تُسلَّم منه إلا ثلاثة حقول: الاسم والمادة والرقم. العمولة ليست مما يحتاجه
 * نموذجُ المجموعة، وإرسالُ الصف كما هو يضعها في كل جواب بلا أن يطلبها أحد.</p>
 */
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    public record TeacherView(Long id, String name, String subject) {
    }

    @GetMapping
    public List<TeacherView> teachers() {
        return teacherService.getAllTeachers().stream()
                .map(TeacherController::view)
                .toList();
    }

    private static TeacherView view(Teacher teacher) {
        return new TeacherView(teacher.getId(), teacher.getName(), teacher.getSubject());
    }
}
