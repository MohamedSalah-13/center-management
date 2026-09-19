package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.dto.MembershipRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حساب حصص العضوية: من يوم الالتحاق إلى يوم الخروج، لا من إنشاء المجموعة.
 *
 * <p>استعلامات {@code @Query} لا يفحصها المُترجم، وهذه بالذات فيها استعلامان فرعيان
 * وشرط على تاريخ خروج قد يكون فارغاً - أخطاؤها لا تظهر كخطأ بل كأرقام حضور خاطئة على
 * كشف يُبنى عليه قرار.</p>
 */
@DataJpaTest
@Import(SecurityConfig.class)
class MembershipQueryTest {

    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private AttendanceRepository attendanceRepository;

    private CourseGroup group;

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم الكشف").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());

        group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة الكشف").teacher(teacher)
                .schoolLevel(SchoolLevel.PREP1)
                .meetingDays(Set.of(DayOfWeek.SATURDAY))
                .startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(18, 0))
                .maxCapacity(20).sessionPrice(new BigDecimal("50.00"))
                .build());
    }

    /**
     * الحصص المنعقدة قبل التحاق الطالب لا تُحسب عليه.
     * بدون هذا الشرط يظهر الملتحق حديثاً غائباً عن حصص لم يكن السنتر يعرفه فيها.
     */
    @Test
    void countsOnlySessionsHeldWhileTheStudentWasEnrolled() {
        session(LocalDate.now().minusDays(20));
        session(LocalDate.now().minusDays(13));

        Student latecomer = student("STU-M1", "طالب ملتحق حديثاً");
        enrol(latecomer, LocalDate.now().minusDays(7), null);

        Session attended = session(LocalDate.now().minusDays(6));
        Session missed = session(LocalDate.now().minusDays(5));
        attend(latecomer, attended);

        List<MembershipRow> roster = studentGroupRepository.findGroupRoster(group.getId());

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).sessionsHeld()).isEqualTo(2);
        assertThat(roster.get(0).sessionsAttended()).isEqualTo(1);
        assertThat(roster.get(0).attendanceRate()).isEqualTo(50);
        assertThat(missed.getId()).isNotNull();
    }

    /** بعد الخروج تتوقف حصص المجموعة عن الاحتساب على الطالب */
    @Test
    void stopsCountingAfterTheLeaveDate() {
        Student left = student("STU-M2", "طالب خرج");
        enrol(left, LocalDate.now().minusDays(20), LocalDate.now().minusDays(10));

        Session inside = session(LocalDate.now().minusDays(15));
        attend(left, inside);
        session(LocalDate.now().minusDays(2));

        List<MembershipRow> memberships = studentGroupRepository.findStudentMemberships(left.getId());

        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).active()).isFalse();
        assertThat(memberships.get(0).leaveDate()).isEqualTo(LocalDate.now().minusDays(10));
        assertThat(memberships.get(0).sessionsHeld()).isEqualTo(1);
        assertThat(memberships.get(0).sessionsAttended()).isEqualTo(1);
    }

    /** الكشف يعرض المشتركين الحاليين وحدهم؛ عضويات الطالب تعرض المنتهية أيضاً */
    @Test
    void rosterShowsCurrentMembersWhileMembershipsShowHistory() {
        Student current = student("STU-M3", "طالب مستمر");
        Student gone = student("STU-M4", "طالب سابق");
        enrol(current, LocalDate.now().minusDays(5), null);
        enrol(gone, LocalDate.now().minusDays(30), LocalDate.now().minusDays(3));

        assertThat(studentGroupRepository.findGroupRoster(group.getId()))
                .extracting(MembershipRow::studentName)
                .containsExactly(current.getName());

        assertThat(studentGroupRepository.findStudentMemberships(gone.getId())).hasSize(1);
    }

    /**
     * عدد المشتركين يوم الحصة: من التحق قبلها ولم يكن قد خرج.
     * هو الرقم الذي يُقرأ بجانب الحضور في كشف مستحقات المعلم.
     */
    @Test
    void countsMembersEnrolledOnTheSessionDate() {
        LocalDate sessionDay = LocalDate.now().minusDays(7);

        enrol(student("STU-M5", "قديم مستمر"), LocalDate.now().minusDays(30), null);
        enrol(student("STU-M6", "خرج قبلها"), LocalDate.now().minusDays(30), LocalDate.now().minusDays(10));
        enrol(student("STU-M7", "خرج بعدها"), LocalDate.now().minusDays(30), LocalDate.now().minusDays(2));
        enrol(student("STU-M8", "التحق بعدها"), LocalDate.now().minusDays(1), null);

        assertThat(studentGroupRepository.countEnrolledOn(group.getId(), sessionDay)).isEqualTo(2);
    }

    /** عدّاد المشتركين لكل مجموعة يتجاهل الاشتراكات المنتهية */
    @Test
    void perGroupCounterIgnoresEndedMemberships() {
        enrol(student("STU-M9", "مشترك"), LocalDate.now().minusDays(3), null);
        enrol(student("STU-M10", "منسحب"), LocalDate.now().minusDays(9), LocalDate.now().minusDays(1));

        assertThat(studentGroupRepository.countActiveMembersPerGroup())
                .anySatisfy(row -> {
                    assertThat(row[0]).isEqualTo(group.getId());
                    assertThat(row[1]).isEqualTo(1L);
                });
    }

    private Student student(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name)
                .schoolLevel(SchoolLevel.PREP1).isActive(true).build());
    }

    private void enrol(Student student, LocalDate joined, LocalDate left) {
        studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student).group(group)
                .joinDate(joined).leaveDate(left)
                .isActive(left == null)
                .build());
    }

    private Session session(LocalDate date) {
        return sessionRepository.saveAndFlush(Session.builder()
                .group(group).sessionDate(date).isActive(false).isPaidOut(false).build());
    }

    private void attend(Student student, Session session) {
        attendanceRepository.saveAndFlush(Attendance.builder()
                .student(student).session(session).timeIn(LocalDateTime.now()).build());
    }
}
