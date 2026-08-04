package com.codejava.center.service;

import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.dto.AttendanceResult;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.DailyAttendance;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TransactionService transactionService;

    /**
     * الدالة الرئيسية التي تستدعيها واجهة JavaFX عند تمرير الباركود.
     *
     * @param barcode          باركود كارنيه الطالب
     * @param boundSessionId   الحصة التي يخدمها هذا الجهاز، أو null لترك النظام يستنتجها
     *                         من مجموعات الطالب بين الحصص المفتوحة اليوم
     */
    @Transactional
    public AttendanceResult processAttendance(String barcode, Long boundSessionId) {

        // 1. التحقق من وجود الطالب
        Optional<Student> studentOpt = studentRepository.findByBarcode(barcode);
        if (studentOpt.isEmpty()) {
            return buildErrorResult(I18n.get("attendance.result.unknownStudent"),
                    I18n.get("attendance.result.barcodeNotRegistered"));
        }
        Student student = studentOpt.get();

        // التحقق من إيقاف حساب الطالب
        if (!student.isActive()) {
            return buildErrorResult(student.getName(), I18n.get("attendance.result.suspended"));
        }

        // 2. تحديد الحصة التي سيُسجَّل عليها الحضور
        Session currentSession;
        if (boundSessionId != null) {
            Optional<Session> bound = sessionRepository.findByIdWithGroup(boundSessionId);
            if (bound.isEmpty() || !bound.get().isActive()) {
                return buildErrorResult(student.getName(), I18n.get("attendance.result.sessionClosed"));
            }
            currentSession = bound.get();
        } else {
            // جهاز واحد يخدم كل القاعات: نبحث عن حصة مفتوحة اليوم ينتمي الطالب لمجموعتها
            List<Session> candidates = sessionRepository.findActiveForStudent(student, LocalDate.now());
            if (candidates.isEmpty()) {
                return buildErrorResult(student.getName(), I18n.get("attendance.result.noActiveSession"));
            }
            if (candidates.size() > 1) {
                String groupNames = candidates.stream()
                        .map(s -> s.getGroup().getName())
                        .collect(Collectors.joining(I18n.get("common.listSeparator") + " "));
                return buildErrorResult(student.getName(),
                        I18n.format("attendance.result.ambiguousSession", groupNames));
            }
            currentSession = candidates.get(0);
        }

        CourseGroup currentGroup = currentSession.getGroup();

        // 3. التأكد من أن الطالب مشترك في هذه المجموعة تحديداً
        boolean isEnrolled = studentGroupRepository.existsByStudentAndGroupAndIsActiveTrue(student, currentGroup);
        if (!isEnrolled) {
            return buildErrorResult(student.getName(),
                    I18n.format("attendance.result.notEnrolled", currentGroup.getName()));
        }

        // 4. التحقق من عدم تسجيل الدخول مسبقاً لنفس الحصة (منع تمرير الكارنيه مرتين)
        // يسبق الفحص المالي عمداً: الطالب الذي دخل بالفعل يجب ألا يُخصم منه مرتين
        // ولا أن يُقال له إن عليه متأخرات
        if (attendanceRepository.existsByStudentAndSession(student, currentSession)) {
            return buildErrorResult(student.getName(), I18n.get("attendance.result.alreadyAttended"));
        }

        // 5. التحقق من الحالة المالية: هل رصيد الطالب يكفي رسوم هذه الحصة؟
        BigDecimal sessionPrice = MoneyUtils.normalize(currentGroup.getSessionPrice());
        BigDecimal balance = transactionService.getStudentBalance(student.getId());
        if (balance.compareTo(sessionPrice) < 0) {
            BigDecimal shortfall = sessionPrice.subtract(balance);
            return buildErrorResult(student.getName(), I18n.format("attendance.result.insufficientBalance",
                    MoneyUtils.format(sessionPrice), MoneyUtils.format(balance), MoneyUtils.format(shortfall)));
        }

        // 6. تسجيل الحضور وخصم رسوم الحصة معاً داخل نفس الـ Transaction
        attendanceRepository.save(Attendance.builder()
                .student(student)
                .session(currentSession)
                .timeIn(LocalDateTime.now())
                .build());

        transactionService.chargeSession(student, currentGroup, currentSession, sessionPrice);

        // 7. إرجاع نتيجة النجاح للواجهة مع الرصيد بعد الخصم
        BigDecimal remaining = balance.subtract(sessionPrice);
        return AttendanceResult.builder()
                .isSuccess(true)
                .studentName(student.getName())
                .groupName(currentGroup.getName())
                .remainingBalance(remaining)
                .message(I18n.format("attendance.result.success", MoneyUtils.formatWithCurrency(remaining)))
                .build();
    }

    /**
     * تقرير حضور وغياب مجموعة خلال فترة.
     * الغياب مشتق: النظام لا يخزّن سجلات غياب، بل يقارن المشتركين النشطين
     * بمن سجّل حضوره فعلاً في حصص الفترة.
     */
    @Transactional(readOnly = true)
    public GroupAttendanceReport getGroupAttendance(CourseGroup group, LocalDate from, LocalDate to) {
        long totalSessions = sessionRepository.countByGroupIdAndSessionDateBetween(group.getId(), from, to);
        List<AttendanceSummary> rows = studentRepository.findGroupAttendance(group.getId(), from, to);

        return new GroupAttendanceReport(group.getName(), totalSessions, rows);
    }

    /**
     * عدد الحضور لكل يوم خلال آخر 7 أيام (لمخطط لوحة القيادة)
     */
    @Transactional(readOnly = true)
    public List<DailyAttendance> getAttendanceLast7Days() {
        return attendanceRepository.countAttendancePerDay(LocalDate.now().minusDays(6));
    }

    // دالة مساعدة لإنشاء رد الرفض
    private AttendanceResult buildErrorResult(String studentName, String errorMessage) {
        return AttendanceResult.builder()
                .isSuccess(false)
                .studentName(studentName)
                .groupName(I18n.get("common.none"))
                .remainingBalance(null)
                .message(errorMessage)
                .build();
    }
}
