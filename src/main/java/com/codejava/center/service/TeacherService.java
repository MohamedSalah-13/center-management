package com.codejava.center.service;

import com.codejava.center.domain.Session;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codejava.center.service.dto.SessionPayout;
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
    private final TransactionService transactionService; // سنستخدمه لتسجيل عملية الدفع

    /**
     * حساب مستحقات المعلم لحصة معينة دون الدفع الفعلي (للمعاينة فقط)
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public BigDecimal calculateSessionPayout(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("الحصة غير موجودة"));

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
            default -> throw new IllegalStateException("نوع عمولة غير معروف: " + teacher.getCommissionType());
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
                .orElseThrow(() -> new IllegalArgumentException("الحصة غير موجودة"));

        if (session.isPaidOut()) {
            throw new IllegalStateException("تم صرف مستحقات هذه الحصة مسبقاً ولا يمكن تكرار الصرف.");
        }

        // الحصة المفتوحة قد يسجّل فيها طلاب آخرون حضورهم بعد، فيكون الإيراد المحسوب
        // ناقصاً ويُصرف للمعلم أقل من حقه دون إمكانية تصحيح (الصرف لا يتكرر)
        if (session.isActive()) {
            throw new IllegalStateException("لا يمكن صرف مستحقات حصة ما زالت مفتوحة. أغلق الحصة أولاً.");
        }

        // 1. حساب المستحقات
        BigDecimal payoutAmount = calculateSessionPayout(sessionId);

        if (payoutAmount.signum() < 0) {
            throw new IllegalStateException("خطأ مالي: قيمة المستحقات بالسالب. يرجى مراجعة إيراد الحصة وقيمة الإيجار.");
        }

        // 2. تسجيل الحركة المالية في الخزينة كمصروف (TEACHER_PAYOUT)
        String description = String.format("تصفية حساب المعلم: %s - حصة: %s - حضور: %d طلاب",
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

    @Transactional
    @RequiresRole(Role.ADMIN)
    public Teacher saveTeacher(Teacher teacher) {
        if (teacher.getName() == null || teacher.getName().isEmpty()) {
            throw new IllegalArgumentException("اسم المعلم مطلوب.");
        }
        if (teacher.getCommissionValue() == null || teacher.getCommissionValue().signum() < 0) {
            throw new IllegalArgumentException("قيمة العمولة مطلوبة ولا يمكن أن تكون سالبة.");
        }
        teacher.setCommissionValue(MoneyUtils.normalize(teacher.getCommissionValue()));
        return teacherRepository.save(teacher);
    }
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void deleteTeacher(Long teacherId) {
        teacherRepository.deleteById(teacherId);
    }
}