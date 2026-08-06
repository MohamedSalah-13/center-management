package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.repository.TransactionRepository;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceOutcome;
import com.codejava.center.service.dto.AttendanceResult;
import com.codejava.center.service.dto.AttendanceState;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * التمريرة الثانية للكارنيه: انصراف، أو ردٌّ لأنها تكرار بالخطأ.
 *
 * <p>ما يُثبَّت هنا هو الحدّ بين الحالتين. بلا مهلة يصير القارئ الذي يقرأ الكارنيه مرتين
 * في الثانية - وهو ما تفعله الأجهزة - مُخرِجاً للطالب في اللحظة التي دخل فيها، فيُقرأ في
 * الكشف أنه مكث صفر دقيقة. وبلا انصراف أصلاً لا يعرف أحد من في المبنى.</p>
 *
 * <p>ويُثبَّت معه أن الانصراف لا يمسّ المال: الخصم وقع عند الدخول، وخصمه ثانيةً عند
 * الخروج يجعل حصةً واحدة بثمن حصتين.</p>
 */
@DataJpaTest
@Import({AttendanceService.class, TransactionService.class, SettingsService.class,
        AuditService.class, UserSession.class, SecurityConfig.class})
class AttendanceCheckOutTest {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TeacherRepository teacherRepository;

    private static final BigDecimal PRICE = new BigDecimal("50.00");

    private CourseGroup group;
    private Session session;
    private Student student;

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00")).build());

        group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة أ").teacher(teacher)
                .maxCapacity(30).sessionPrice(PRICE).build());

        session = sessionRepository.saveAndFlush(Session.builder()
                .group(group).sessionDate(LocalDate.now())
                .startedAt(LocalDateTime.now()).isActive(true).isPaidOut(false).build());

        student = studentRepository.saveAndFlush(Student.builder()
                .barcode("STU-CO1").name("طالب").parentPhone("0111").isActive(true).build());
        studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student).group(group).joinDate(LocalDate.now()).isActive(true).build());

        topUp(new BigDecimal("200.00"));
    }

    /** التمريرة الأولى دخول: يُكتب وقت الدخول وتُخصم الرسوم، ولا وقت انصراف بعد */
    @Test
    void firstScanChecksTheStudentIn() {
        AttendanceResult result = attendanceService.processAttendance("STU-CO1", session.getId());

        assertThat(result.getOutcome()).isEqualTo(AttendanceOutcome.CHECKED_IN);
        assertThat(result.getRow().state()).isEqualTo(AttendanceState.INSIDE);
        assertThat(onlyAttendance().getTimeOut()).isNull();
        assertThat(sessionCharges()).isEqualTo(1);
    }

    /**
     * التمريرة المكرّرة داخل المهلة لا تُخرج أحداً.
     * هذه هي الحالة التي تجعل الميزة عطلاً لو سقطت: القارئ يقرأ مرتين من نفسه.
     */
    @Test
    void secondScanWithinTheGracePeriodIsARepeatNotACheckOut() {
        attendanceService.processAttendance("STU-CO1", session.getId());

        AttendanceResult result = attendanceService.processAttendance("STU-CO1", session.getId());

        assertThat(result.getOutcome()).isEqualTo(AttendanceOutcome.REJECTED);
        assertThat(result.getMessage()).isEqualTo(I18n.get("attendance.result.duplicateScan"));
        assertThat(onlyAttendance().getTimeOut()).isNull();
    }

    /** بعد انقضاء المهلة تصير التمريرة الثانية انصرافاً، بلا حركة مالية جديدة */
    @Test
    void secondScanAfterTheGracePeriodChecksTheStudentOut() {
        attendanceService.processAttendance("STU-CO1", session.getId());
        backdateCheckIn(30);

        AttendanceResult result = attendanceService.processAttendance("STU-CO1", session.getId());

        assertThat(result.getOutcome()).isEqualTo(AttendanceOutcome.CHECKED_OUT);
        assertThat(onlyAttendance().getTimeOut()).isNotNull();
        assertThat(result.getRow().state()).isEqualTo(AttendanceState.LEFT);

        // الخصم وقع عند الدخول؛ الخروج لا يُخصم منه شيء ولا يُذكر له رصيد
        assertThat(sessionCharges()).isEqualTo(1);
        assertThat(result.getRemainingBalance()).isNull();
    }

    /** التمريرة الثالثة تُردّ: لا دخول ثانٍ ولا انصراف ثانٍ لحصة واحدة */
    @Test
    void thirdScanIsRefusedBecauseTheStudentAlreadyLeft() {
        attendanceService.processAttendance("STU-CO1", session.getId());
        backdateCheckIn(30);
        attendanceService.processAttendance("STU-CO1", session.getId());

        AttendanceResult result = attendanceService.processAttendance("STU-CO1", session.getId());

        assertThat(result.getOutcome()).isEqualTo(AttendanceOutcome.REJECTED);
        assertThat(result.getMessage()).isEqualTo(I18n.get("attendance.result.alreadyCheckedOut"));
        assertThat(sessionCharges()).isEqualTo(1);
    }

    /** زرّ الصفّ في الشاشة: انصرافٌ بلا مهلة، لمن نسي تمرير كارنيهه عند خروجه */
    @Test
    void theRowButtonChecksOutWithoutWaitingForTheGracePeriod() {
        attendanceService.processAttendance("STU-CO1", session.getId());
        Long attendanceId = onlyAttendance().getId();

        AttendanceResult result = attendanceService.checkOut(attendanceId);

        assertThat(result.getOutcome()).isEqualTo(AttendanceOutcome.CHECKED_OUT);
        assertThat(onlyAttendance().getTimeOut()).isNotNull();

        assertThatThrownBy(() -> attendanceService.checkOut(attendanceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.attendance.alreadyCheckedOut", student.getName()));
    }

    /**
     * الحصة المغلقة تحوّل من لم يُسجَّل انصرافه إلى "لم يُسجَّل" لا إلى "بالداخل":
     * كشفُ الأسبوع الماضي بلا هذا الفرق يقول إن نصف السنتر ما زالوا في القاعات.
     */
    @Test
    void closingTheSessionLeavesNoInventedCheckOutTime() {
        attendanceService.processAttendance("STU-CO1", session.getId());

        session.setActive(false);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.saveAndFlush(session);

        List<AttendanceLogRow> log = attendanceService.getTodayLog();

        assertThat(log).hasSize(1);
        assertThat(log.get(0).timeOut()).isNull();
        assertThat(log.get(0).state()).isEqualTo(AttendanceState.NOT_RECORDED);
        assertThat(log.get(0).duration()).isNull();
    }

    private Attendance onlyAttendance() {
        attendanceRepository.flush();
        List<Attendance> all = attendanceRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    /** تقديم وقت الدخول بدل انتظار المهلة الحقيقية */
    private void backdateCheckIn(int minutes) {
        Attendance attendance = onlyAttendance();
        attendance.setTimeIn(LocalDateTime.now().minusMinutes(minutes));
        attendanceRepository.saveAndFlush(attendance);
    }

    private long sessionCharges() {
        return transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getType() == TransactionType.SESSION_CHARGE)
                .count();
    }

    private void topUp(BigDecimal amount) {
        transactionRepository.saveAndFlush(Transaction.builder()
                .type(TransactionType.INCOME)
                .amount(amount)
                .description("رصيد افتتاحي")
                .transactionDate(LocalDateTime.now())
                .student(student)
                .group(group)
                .build());
    }
}
