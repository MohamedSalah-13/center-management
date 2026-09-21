package com.codejava.center.service;

import com.codejava.center.domain.Session;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.service.dto.TeacherDraft;
import com.codejava.center.util.I18n;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.MoneyUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TransactionService transactionService; // سنستخدمه لتسجيل عملية الدفع
    private final AuditService auditService;

    /**
     * حساب مستحقات المعلم لحصة معينة دون الدفع الفعلي (للمعاينة فقط)
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public BigDecimal calculateSessionPayout(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.session.notFound")));

        return calculatePayout(session, attendanceRepository.countBySession(session));
    }

    /**
     * خوارزمية العمولة حسب نوع اتفاق المعلم.
     * مفصولة عن calculateSessionPayout حتى تستطيع شاشة الصرف حساب عدة حصص
     * دون إعادة جلب كل حصة وعدّ حضورها مرتين.
     */
    private BigDecimal calculatePayout(Session session, long attendeesCount) {
        Teacher teacher = session.getGroup().getTeacher();

        // إجمالي الإيراد = عدد الحضور * سعر الحصة المخصص للمجموعة
        BigDecimal totalRevenue = MoneyUtils.normalize(session.getGroup().getSessionPrice())
                .multiply(BigDecimal.valueOf(attendeesCount));
        BigDecimal commissionValue = MoneyUtils.normalize(teacher.getCommissionValue());

        BigDecimal payout = switch (teacher.getCommissionType()) {
            case "PERCENTAGE" -> totalRevenue.multiply(commissionValue)
                    .divide(BigDecimal.valueOf(100), MoneyUtils.SCALE, RoundingMode.HALF_UP);
            case "FIXED_AMOUNT" -> commissionValue;
            case "RENT" -> totalRevenue.subtract(commissionValue);
            default -> throw new IllegalStateException(
                    I18n.format("error.teacher.unknownCommission", teacher.getCommissionType()));
        };

        return MoneyUtils.normalize(payout);
    }

    /**
     * تنفيذ عملية الدفع وإغلاق حساب الحصة
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void processSessionPayout(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.session.notFound")));

        if (session.isPaidOut()) {
            throw new IllegalStateException(I18n.get("error.teacher.payoutAlreadyDone"));
        }

        // الحصة المفتوحة قد يسجّل فيها طلاب آخرون حضورهم بعد، فيكون الإيراد المحسوب
        // ناقصاً ويُصرف للمعلم أقل من حقه دون إمكانية تصحيح (الصرف لا يتكرر)
        if (session.isActive()) {
            throw new IllegalStateException(I18n.get("error.teacher.sessionStillOpen"));
        }

        // 1. حساب المستحقات
        BigDecimal payoutAmount = calculateSessionPayout(sessionId);

        if (payoutAmount.signum() < 0) {
            throw new IllegalStateException(I18n.get("error.teacher.negativePayout"));
        }

        // 2. تسجيل الحركة المالية في الخزينة كمصروف (TEACHER_PAYOUT)
        String description = I18n.format("teacher.payoutDescription",
                session.getGroup().getTeacher().getName(),
                session.getSessionDate().toString(),
                attendanceRepository.countBySession(session));

        transactionService.recordTeacherPayout(payoutAmount, description);

        // 3. إغلاق الحصة حتى لا يتم الدفع مرتين
        session.setPaidOut(true);
        sessionRepository.save(session);
    }

    /**
     * الحصص المغلقة التي لم تُصرَف مستحقاتها بعد، ومع كل واحدة المبلغ المحسوب.
     * عدد الحصص هنا محدود بطبيعته لأن التصفية تتم دورياً، فحساب الحضور لكل حصة مقبول.
     */
    /** نفس القائمة مقصورة على معلم واحد، لكشف حسابه */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<SessionPayout> getPayableSessionsOf(Long teacherId) {
        return getPayableSessions().stream()
                .filter(p -> teacherId.equals(p.teacherId()))
                .toList();
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<SessionPayout> getPayableSessions() {
        return sessionRepository.findPayableSessions().stream()
                .map(session -> {
                    Teacher teacher = session.getGroup().getTeacher();
                    long attendees = attendanceRepository.countBySession(session);
                    BigDecimal revenue = MoneyUtils.normalize(session.getGroup().getSessionPrice())
                            .multiply(BigDecimal.valueOf(attendees));

                    return new SessionPayout(
                            session.getId(),
                            session.getGroup().getName(),
                            teacher.getId(),
                            teacher.getName(),
                            session.getSessionDate(),
                            attendees,
                            // معلومة تُقرأ بجانب الحضور لا مُدخل في الحساب:
                            // المستحق يبقى محكوماً باتفاق المعلم وحده
                            studentGroupRepository.countEnrolledOn(
                                    session.getGroup().getId(), session.getSessionDate()),
                            teacher.getCommissionType(),
                            MoneyUtils.normalize(revenue),
                            calculatePayout(session, attendees));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<Teacher> getAllTeachers() {
        return teacherRepository.findAll();
    }

    /**
     * معلمٌ بمعرّفه، لكشف حسابه.
     *
     * <p>لازمةٌ لمن يصل إليه برقمٍ لا بكائن - أي كلُّ حافة HTTP: شاشةُ سطح المكتب
     * تحمل الصفَّ الذي اختاره المستخدم من قائمةٍ قرأتها توّاً.</p>
     *
     * <p>ومحروسةٌ كـ {@code getAllTeachers}، لا مفتوحةٌ كبقية القراءات: الصفُّ يحمل
     * نوع العمولة وقيمتها - أي ما يتقاضاه المعلم - وهو لا يُقرأ هنا إلا لبناء ورقة
     * صرفٍ لا يوقّعها غير صاحب السنتر.</p>
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public Teacher findById(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.teacher.notFound")));
    }

    /**
     * حفظ معلم جديد أو تعديل قائم.
     *
     * <p>المدخل {@link TeacherDraft} لا الكيان - انظر تعليقه - والصفُّ القائم يُقرأ ثم
     * يُطبَّق عليه ما فيها، كما في {@code saveStudent} و{@code saveGroup}.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public Teacher saveTeacher(TeacherDraft draft) {
        String name = draft.trimmedName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(I18n.get("error.teacher.nameRequired"));
        }
        if (draft.commissionValue() == null || draft.commissionValue().signum() < 0) {
            throw new IllegalArgumentException(I18n.get("error.teacher.commissionRequired"));
        }
        // نوعُ العمولة يقرّر ما يعنيه الرقم: نسبةً، أو مبلغاً ثابتاً، أو إيجارَ قاعة.
        // ونوعٌ لا يعرفه الحساب يجعل كل صرفٍ لهذا المعلم يسقط برسالةٍ عند الصرف لا هنا
        CommissionTypes.requireKnown(draft.commissionType());

        boolean isNew = draft.isNew();
        Teacher teacher = isNew
                ? new Teacher()
                : teacherRepository.findById(draft.id())
                        .orElseThrow(() -> new IllegalStateException(I18n.get("error.teacher.notFound")));

        teacher.setName(name);
        teacher.setSubject(draft.subject());
        teacher.setCommissionType(draft.commissionType());
        teacher.setCommissionValue(MoneyUtils.normalize(draft.commissionValue()));

        Teacher saved = teacherRepository.save(teacher);

        // اتفاق العمولة يُسجَّل بقيمته: تغييره يغيّر ما يُصرف من الخزينة عن كل حصة
        auditService.record(isNew ? AuditAction.TEACHER_CREATED : AuditAction.TEACHER_UPDATED,
                saved.getId(), saved.getName(),
                "commission=" + saved.getCommissionType()
                        + "; value=" + MoneyUtils.format(saved.getCommissionValue()));

        return saved;
    }
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void deleteTeacher(Long teacherId) {
        String name = teacherRepository.findById(teacherId).map(Teacher::getName).orElse(null);

        try {
            teacherRepository.deleteById(teacherId);
            // المؤجَّل إلى commit لا يبقى له موضعٌ يُترجَم فيه؛ flush يُوقعه داخل هذا الحارس
            teacherRepository.flush();
        } catch (DataIntegrityViolationException error) {
            // معلمٌ له مجموعة لا يُحذف: حذفُه يترك مجموعاتٍ بلا صاحب، والاسم مطبوعٌ في
            // كشوف حسابٍ سابقة. والشاشة كانت تقول ذلك بنفسها لكل خطأ أياً كان سببه
            throw new IllegalStateException(I18n.get("teacher.deleteBlocked"), error);
        }
        auditService.record(AuditAction.TEACHER_DELETED, teacherId, name);
    }
}