package com.codejava.center.service;

import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceOutcome;
import com.codejava.center.service.dto.AttendanceResult;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.DailyAttendance;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.util.Durations;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
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
     * المهلة التي تُعتبر التمريرة داخلها تكراراً بالخطأ لا انصرافاً.
     *
     * <p>ثابتٌ لا إعداد: ليست سياسةً للسنتر يختارها صاحبه، بل خاصيةٌ في قارئ الباركود
     * نفسه - يقرأ الكارنيه مرتين في الثانية إن تُرك أمامه لحظة زائدة.</p>
     */
    static final Duration CHECKOUT_GRACE = Duration.ofMinutes(2);

    /**
     * الدالة الرئيسية التي تستدعيها واجهة JavaFX عند تمرير الباركود.
     *
     * <p>التمريرة الأولى دخول، والثانية على نفس الحصة انصراف. قارئٌ واحد يخدم البابين
     * لأنه ما في السنتر فعلاً: لا مفتاح وضعٍ يُنسى على "انصراف" فيخرج به من دخل للتوّ.</p>
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

        // 4. الطالب الذي له صفٌّ في هذه الحصة: تمريرته الثانية انصراف لا دخول.
        // يسبق الفحص المالي عمداً: من دخل بالفعل يجب ألا يُخصم منه مرتين، ولا أن يُقال
        // له إن عليه متأخرات وهو خارج من الحصة التي دفع ثمنها
        Optional<Attendance> existing = attendanceRepository.findByStudentAndSession(student, currentSession);
        if (existing.isPresent()) {
            return handleSecondScan(existing.get(), student, currentSession, currentGroup);
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
        Attendance saved = attendanceRepository.save(Attendance.builder()
                .student(student)
                .session(currentSession)
                .timeIn(LocalDateTime.now())
                .build());

        transactionService.chargeSession(student, currentGroup, currentSession, sessionPrice);

        // 7. إرجاع نتيجة النجاح للواجهة مع الرصيد بعد الخصم
        BigDecimal remaining = balance.subtract(sessionPrice);
        return AttendanceResult.builder()
                .outcome(AttendanceOutcome.CHECKED_IN)
                .studentName(student.getName())
                .groupName(currentGroup.getName())
                .remainingBalance(remaining)
                .message(I18n.format("attendance.result.success", MoneyUtils.formatWithCurrency(remaining)))
                .row(toLogRow(saved, student, currentSession, currentGroup))
                .build();
    }

    /**
     * التمريرة الثانية على نفس الحصة: انصراف، أو ردٌّ لطيف.
     *
     * <p>المهلة ({@link #CHECKOUT_GRACE}) هي الفرق بين الميزة والعطل: قارئ الباركود يقرأ
     * الكارنيه مرتين في الثانية أحياناً، وبلا مهلة يخرج الطالب في اللحظة التي دخل فيها -
     * فيُقرأ في الكشف أنه مكث صفر دقيقة. وداخل المهلة تُقرأ التمريرة على أنها ما هي عليه
     * في الواقع: يدٌ مرّرت الكارنيه مرتين.</p>
     */
    private AttendanceResult handleSecondScan(Attendance attendance, Student student,
                                              Session session, CourseGroup group) {
        if (attendance.getTimeOut() != null) {
            return buildErrorResult(student.getName(), I18n.get("attendance.result.alreadyCheckedOut"));
        }

        LocalDateTime now = LocalDateTime.now();
        if (Duration.between(attendance.getTimeIn(), now).compareTo(CHECKOUT_GRACE) < 0) {
            return buildErrorResult(student.getName(), I18n.get("attendance.result.duplicateScan"));
        }

        attendance.setTimeOut(now);
        attendanceRepository.save(attendance);

        return checkOutResult(attendance, student, session, group);
    }

    /**
     * تسجيل الانصراف من زرّ الصفّ في الشاشة، لمن نسي تمرير كارنيهه عند خروجه.
     *
     * <p>بلا مهلة: هذه ضغطةُ إنسانٍ ينظر إلى الاسم، لا تمريرةُ قارئٍ قد تتكرّر من نفسها.</p>
     */
    @Transactional
    public AttendanceResult checkOut(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.attendance.notFound")));

        Student student = attendance.getStudent();
        if (attendance.getTimeOut() != null) {
            throw new IllegalStateException(I18n.format("error.attendance.alreadyCheckedOut", student.getName()));
        }

        attendance.setTimeOut(LocalDateTime.now());
        attendanceRepository.save(attendance);

        Session session = attendance.getSession();
        return checkOutResult(attendance, student, session, session.getGroup());
    }

    private AttendanceResult checkOutResult(Attendance attendance, Student student,
                                            Session session, CourseGroup group) {
        // لا رصيد في الجواب ولا حركة مالية: الخصم وقع عند الدخول، والمكوث ليس سلعة تُباع
        return AttendanceResult.builder()
                .outcome(AttendanceOutcome.CHECKED_OUT)
                .studentName(student.getName())
                .groupName(group.getName())
                .message(I18n.format("attendance.result.checkedOut",
                        Durations.format(Duration.between(attendance.getTimeIn(), attendance.getTimeOut()))))
                .row(toLogRow(attendance, student, session, group))
                .build();
    }

    private AttendanceLogRow toLogRow(Attendance attendance, Student student,
                                      Session session, CourseGroup group) {
        return new AttendanceLogRow(attendance.getId(), student.getName(), student.getBarcode(),
                student.getParentPhone(), group.getName(), session.getSessionDate(),
                attendance.getTimeIn(), attendance.getTimeOut(), session.isActive());
    }

    /**
     * كشف الحضور والانصراف خلال فترة، لمجموعة بعينها أو لكل المجموعات.
     *
     * <p>تخدم الشاشتين معاً: شاشة البوابة تسأل عن اليوم وحده فتفتح على حاله - وهي التي
     * كانت تُفرغ جدولها في كل تنقّل لأن الجدول كان في الذاكرة لا في قاعدة البيانات -
     * وشاشة الكشف تسأل عن أي فترة مضت.</p>
     *
     * @param groupId مجموعة بعينها، أو {@code null} لكل المجموعات
     */
    @Transactional(readOnly = true)
    public List<AttendanceLogRow> getAttendanceLog(LocalDate from, LocalDate to, Long groupId) {
        return attendanceRepository.findAttendanceLog(from, to, groupId);
    }

    /** كشف اليوم كما تعرضه شاشة البوابة عند فتحها */
    @Transactional(readOnly = true)
    public List<AttendanceLogRow> getTodayLog() {
        LocalDate today = LocalDate.now();
        return attendanceRepository.findAttendanceLog(today, today, null);
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
                .outcome(AttendanceOutcome.REJECTED)
                .studentName(studentName)
                .groupName(I18n.get("common.none"))
                .remainingBalance(null)
                .message(errorMessage)
                .build();
    }
}
