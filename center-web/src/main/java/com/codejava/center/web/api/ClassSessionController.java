package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.SessionService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * فتحُ الحصة وإغلاقها.
 *
 * <p>هذا ما كان يجعل بوابة الحضور على الويب نصفَ ميزة: التمريرة تحتاج حصةً مفتوحة،
 * ولم يكن يفتحها إلا جهاز سطح المكتب. فسنترٌ يريد شاشاته على الشبكة كان يحتاج جهازاً
 * واحداً على الأقل ليبدأ يومه.</p>
 *
 * <p><b>والمسار {@code /api/class-sessions} لا {@code /api/sessions}</b>، مع أن الكيان
 * اسمه {@code Session}: {@code /api/session} تعني جلسةَ من سجّل دخوله، ومعنيان لكلمةٍ
 * واحدة تحت بادئةٍ واحدة خطأٌ ينتظر من يكتب {@code /api/session} وهو يقصد الحصة. الاسم
 * في الكود يتبع الكيان، والاسم في الرابط يتبع ما يفرّق.</p>
 *
 * <p>ولا قرار هنا: "هل للمجموعة حصة مفتوحة"، و"هل لها حصة في هذا اليوم"، وسباقُ
 * جهازين يفتحان معاً - كلُّها في {@code SessionService} برسائلها، وكلٌّ منها يقول
 * للمستخدم ما يفعله بعدها.</p>
 */
@RestController
@RequestMapping("/api/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final SessionService sessionService;
    private final CourseGroupService courseGroupService;

    /**
     * @param date اليوم الذي تُحسب له الحصة، وقد يسبق اليوم الجاري: كشفُ يومٍ مضى
     *             يُفتح أحياناً بعد وقوعه. {@code null} يعني اليوم
     */
    public record OpenRequest(@NotNull Long groupId,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    }

    public record SessionView(Long id, Long groupId, String groupName, String teacherName,
                              LocalDate date, LocalDateTime startedAt, LocalDateTime endedAt,
                              boolean open) {
    }

    /**
     * @param date  يومٌ بعينه، أو الكل
     * @param open  {@code true} المفتوحة، {@code false} المغلقة، وغيابُه الكل
     */
    @GetMapping
    public List<SessionView> sessions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Boolean open) {
        return sessionService.findSessions(date, open).stream()
                .map(ClassSessionController::view)
                .toList();
    }

    @PostMapping
    public SessionView open(@RequestBody OpenRequest request) {
        CourseGroup group = courseGroupService.findById(request.groupId());
        return view(sessionService.openSession(group, request.date()));
    }

    /**
     * الإغلاق لا يخترع وقت انصراف لمن لم يمرّر كارنيهه.
     *
     * <p>ختمُهم بوقت نهاية الحصة يجعل من غادر في السابعة يبدو باقياً حتى العاشرة،
     * وذلك الرقم يُقرأ بعد شهر على أنه قياس. الخدمة تتركه فارغاً، و
     * {@code AttendanceState} هي التي تفرّق بين "بالداخل الآن" و"لم يُسجَّل انصرافه".</p>
     */
    @PostMapping("/{id}/close")
    public SessionView close(@PathVariable Long id) {
        sessionService.closeSession(id);
        return view(sessionService.findById(id));
    }

    private static SessionView view(Session session) {
        CourseGroup group = session.getGroup();
        return new SessionView(session.getId(),
                group == null ? null : group.getId(),
                group == null ? null : group.getName(),
                group == null || group.getTeacher() == null ? null : group.getTeacher().getName(),
                session.getSessionDate(), session.getStartedAt(), session.getEndedAt(),
                session.isActive());
    }
}
