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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;

    /**
     * الدالة الرئيسية التي تستدعيها واجهة JavaFX عند تمرير الباركود
     */
    @Transactional
    public AttendanceResult processAttendance(String barcode) {

        // 1. التحقق من وجود الطالب
        Optional<Student> studentOpt = studentRepository.findByBarcode(barcode);
        if (studentOpt.isEmpty()) {
            return buildErrorResult("غير معروف", "باركود غير مسجل بالنظام");
        }
        Student student = studentOpt.get();

        // التحقق من إيقاف حساب الطالب
        if (!student.isActive()) {
            return buildErrorResult(student.getName(), "حساب الطالب موقوف من الإدارة");
        }

        // 2. البحث عن حصة مفتوحة (Active Session) في الوقت الحالي
        // في الواقع العملي: تقوم السكرتارية بـ "فتح" حصة معينة للمجموعة قبل دخول الطلاب
        Optional<Session> activeSessionOpt = sessionRepository.findActiveSessionForToday();
        if (activeSessionOpt.isEmpty()) {
            return buildErrorResult(student.getName(), "لا توجد حصة مفعلة حالياً لدخول الطلاب");
        }
        Session currentSession = activeSessionOpt.get();
        CourseGroup currentGroup = currentSession.getGroup();

        // 3. التأكد من أن الطالب مشترك في هذه المجموعة تحديداً
        boolean isEnrolled = studentGroupRepository.existsByStudentAndGroup(student, currentGroup);
        if (!isEnrolled) {
            return buildErrorResult(student.getName(), "الطالب غير مسجل في مجموعة: " + currentGroup.getName());
        }

        // 4. التحقق من الحالة المالية (هل تم دفع مصروفات الشهر/الحصة؟)
        if (!hasPaidForSession(student, currentGroup)) {
            return buildErrorResult(student.getName(), "مرفوض: تأخير في سداد المصروفات");
        }

        // 5. التحقق من عدم تسجيل الدخول مسبقاً لنفس الحصة (منع تمرير الكارنيه مرتين)
        boolean alreadyAttended = attendanceRepository.existsByStudentAndSession(student, currentSession);
        if (alreadyAttended) {
            return buildErrorResult(student.getName(), "تم تسجيل دخول هذا الطالب بالفعل للحصة الحالية");
        }

        // 6. تسجيل الحضور في قاعدة البيانات
        Attendance newAttendance = Attendance.builder()
                .student(student)
                .session(currentSession)
                .timeIn(LocalDateTime.now())
                .build();
        attendanceRepository.save(newAttendance);

        // 7. إرجاع نتيجة النجاح للواجهة
        return AttendanceResult.builder()
                .isSuccess(true)
                .studentName(student.getName())
                .groupName(currentGroup.getName())
                .message("تم التسجيل بنجاح")
                .build();
    }

    // دالة مساعدة لإنشاء رد الرفض
    private AttendanceResult buildErrorResult(String studentName, String errorMessage) {
        return AttendanceResult.builder()
                .isSuccess(false)
                .studentName(studentName)
                .groupName("---")
                .message(errorMessage)
                .build();
    }

    // دالة للتحقق من الدفع (تُربط لاحقاً بجدول المالية Transactions)
    private boolean hasPaidForSession(Student student, CourseGroup group) {
        // سيتم وضع منطق الفحص المالي هنا (مثل فحص إيصالات الدفع للطالب في هذا الشهر)
        // مؤقتاً نعيد true لنجاح الاختبار
        return true;
    }
}