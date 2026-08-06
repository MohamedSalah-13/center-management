package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Teacher;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceState;
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

/**
 * إسقاط كشف الحضور والانصراف. JPQL يُترجَم وقت التشغيل، واسم حقل خاطئ في تعبير
 * {@code new ...(...)} لا يظهر إلا حين يفتح الموظف الشاشة عند العميل.
 *
 * <p>وما يُثبَّت معه أن التصفية تقع في قاعدة البيانات: بالفترة وبالمجموعة. لو رجعت
 * الشاشة صفوف السنتر كلها لتُصفّى في الذاكرة لبدت صحيحة اليوم، وثقلت بعد سنة إلى أن
 * تتوقف - و{@code attendances} أسرع جداول السنتر نمواً.</p>
 */
@DataJpaTest
@Import(SecurityConfig.class)
class AttendanceLogQueryTest {

    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    private CourseGroup first;
    private CourseGroup second;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00")).build());

        first = group(teacher, "مجموعة أ");
        second = group(teacher, "مجموعة ب");
    }

    /** الصف يحمل ما تعرضه الشاشة كاملاً: الطالب ومجموعته ووقتاه */
    @Test
    void theProjectionCarriesTheStudentTheGroupAndBothTimes() {
        Session session = session(first, today, true);
        Student student = student("STU-L1", "طالب أول");
        LocalDateTime timeIn = LocalDateTime.now().minusHours(2);
        attend(student, session, timeIn, timeIn.plusMinutes(90));

        List<AttendanceLogRow> rows = attendanceRepository.findAttendanceLog(today, today, null);

        assertThat(rows).hasSize(1);
        AttendanceLogRow row = rows.get(0);
        assertThat(row.studentName()).isEqualTo("طالب أول");
        assertThat(row.barcode()).isEqualTo("STU-L1");
        assertThat(row.parentPhone()).isEqualTo("0111");
        assertThat(row.groupName()).isEqualTo("مجموعة أ");
        assertThat(row.sessionDate()).isEqualTo(today);
        assertThat(row.timeOut()).isNotNull();
        assertThat(row.state()).isEqualTo(AttendanceState.LEFT);
    }

    /**
     * {@code sessionActive} ليس زينة في الإسقاط: هو ما يفرّق بين "بالداخل الآن"
     * و"غادر ولم يمرّر كارنيهه"، وكلاهما بلا وقت انصراف.
     */
    @Test
    void anEmptyCheckOutMeansInsideWhileTheSessionIsOpenAndUnrecordedAfterItCloses() {
        Session open = session(first, today, true);
        Session closed = session(second, today, false);

        attend(student("STU-L2", "ما زال بالداخل"), open, LocalDateTime.now().minusMinutes(20), null);
        attend(student("STU-L3", "غادر بلا تسجيل"), closed, LocalDateTime.now().minusHours(3), null);

        List<AttendanceLogRow> rows = attendanceRepository.findAttendanceLog(today, today, null);

        assertThat(stateOf(rows, "ما زال بالداخل")).isEqualTo(AttendanceState.INSIDE);
        assertThat(stateOf(rows, "غادر بلا تسجيل")).isEqualTo(AttendanceState.NOT_RECORDED);
    }

    /** الحصص خارج الفترة لا تظهر */
    @Test
    void attendanceOutsideTheRangeIsExcluded() {
        attend(student("STU-L4", "أمس"), session(first, today.minusDays(1), false),
                LocalDateTime.now().minusDays(1), null);
        attend(student("STU-L5", "اليوم"), session(first, today, true),
                LocalDateTime.now(), null);

        assertThat(attendanceRepository.findAttendanceLog(today, today, null))
                .extracting(AttendanceLogRow::studentName)
                .containsExactly("اليوم");
    }

    /** التصفية بالمجموعة، و null تعني كل المجموعات */
    @Test
    void filteringByGroupNarrowsTheLogAndNullMeansEveryGroup() {
        attend(student("STU-L6", "من الأولى"), session(first, today, true), LocalDateTime.now(), null);
        attend(student("STU-L7", "من الثانية"), session(second, today, true), LocalDateTime.now(), null);

        assertThat(attendanceRepository.findAttendanceLog(today, today, first.getId()))
                .extracting(AttendanceLogRow::studentName)
                .containsExactly("من الأولى");

        assertThat(attendanceRepository.findAttendanceLog(today, today, null))
                .extracting(AttendanceLogRow::studentName)
                .containsExactlyInAnyOrder("من الأولى", "من الثانية");
    }

    /** الأحدث دخولاً أولاً: من دخل قبل قليل هو من يُسأل عنه على الشاشة */
    @Test
    void theMostRecentCheckInComesFirst() {
        Session session = session(first, today, true);
        attend(student("STU-L8", "قديم"), session, LocalDateTime.now().minusHours(3), null);
        attend(student("STU-L9", "حديث"), session, LocalDateTime.now().minusMinutes(5), null);

        assertThat(attendanceRepository.findAttendanceLog(today, today, null))
                .extracting(AttendanceLogRow::studentName)
                .containsExactly("حديث", "قديم");
    }

    private AttendanceState stateOf(List<AttendanceLogRow> rows, String studentName) {
        return rows.stream()
                .filter(row -> row.studentName().equals(studentName))
                .findFirst().orElseThrow()
                .state();
    }

    private CourseGroup group(Teacher teacher, String name) {
        return courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name(name).teacher(teacher)
                .maxCapacity(30).sessionPrice(new BigDecimal("50.00")).build());
    }

    private Session session(CourseGroup group, LocalDate date, boolean active) {
        return sessionRepository.saveAndFlush(Session.builder()
                .group(group).sessionDate(date).isActive(active).isPaidOut(false).build());
    }

    private Student student(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name).parentPhone("0111").isActive(true).build());
    }

    private void attend(Student student, Session session, LocalDateTime timeIn, LocalDateTime timeOut) {
        attendanceRepository.saveAndFlush(Attendance.builder()
                .student(student).session(session).timeIn(timeIn).timeOut(timeOut).build());
    }
}
