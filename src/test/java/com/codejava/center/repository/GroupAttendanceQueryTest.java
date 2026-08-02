package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.*;
import com.codejava.center.service.dto.AttendanceSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تقرير الحضور والغياب. الغياب مشتق لا مخزَّن، فالخطر الأساسي أن يسقط
 * الطالب الغائب من النتيجة تماماً وهو بالضبط من يبحث عنه التقرير.
 */
@DataJpaTest
@Import(SecurityConfig.class)
class GroupAttendanceQueryTest {

    @Autowired private StudentRepository studentRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    private CourseGroup group;
    private final LocalDate from = LocalDate.now().minusDays(7);
    private final LocalDate to = LocalDate.now();

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00")).build());
        group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة أ").teacher(teacher)
                .maxCapacity(30).sessionPrice(new BigDecimal("50.00")).build());
    }

    /** الطالب الذي لم يحضر أي حصة يجب أن يظهر بصفر لا أن يختفي */
    @Test
    void studentWhoNeverAttendedStillAppearsWithZero() {
        Student absentee = enrol("STU-G1", "غائب دائم");

        Session s1 = session(LocalDate.now().minusDays(2));
        Student present = enrol("STU-G2", "حاضر");
        attend(present, s1);

        var rows = studentRepository.findGroupAttendance(group.getId(), from, to);

        assertThat(rows).extracting(AttendanceSummary::studentName)
                .containsExactlyInAnyOrder("غائب دائم", "حاضر");
        assertThat(rowFor(rows, absentee).attended()).isZero();
        assertThat(rowFor(rows, present).attended()).isEqualTo(1);
    }

    /** الحصص خارج الفترة لا تُحتسب */
    @Test
    void attendanceOutsideTheRangeIsNotCounted() {
        Student student = enrol("STU-G3", "طالب");
        attend(student, session(LocalDate.now().minusDays(2)));      // داخل الفترة
        attend(student, session(LocalDate.now().minusDays(30)));     // خارجها

        var rows = studentRepository.findGroupAttendance(group.getId(), from, to);

        assertThat(rowFor(rows, student).attended()).isEqualTo(1);
    }

    /** الاشتراك الملغى لا يظهر في كشف المجموعة */
    @Test
    void cancelledMembershipsAreExcluded() {
        Student left = enrol("STU-G4", "منسحب");
        StudentGroup membership = studentGroupRepository.findByStudentAndGroup(left, group).orElseThrow();
        membership.setActive(false);
        studentGroupRepository.saveAndFlush(membership);

        assertThat(studentRepository.findGroupAttendance(group.getId(), from, to)).isEmpty();
    }

    /** عدد الحصص المنعقدة هو مقام نسبة الحضور */
    @Test
    void sessionCountCoversOnlyTheRequestedRangeAndGroup() {
        session(LocalDate.now().minusDays(1));
        session(LocalDate.now().minusDays(3));
        session(LocalDate.now().minusDays(40)); // خارج الفترة

        assertThat(sessionRepository.countByGroupIdAndSessionDateBetween(group.getId(), from, to))
                .isEqualTo(2);
    }

    private AttendanceSummary rowFor(java.util.List<AttendanceSummary> rows, Student student) {
        return rows.stream().filter(r -> r.studentId().equals(student.getId())).findFirst().orElseThrow();
    }

    private Student enrol(String barcode, String name) {
        Student student = studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name).parentPhone("0111").isActive(true).build());
        studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student).group(group).joinDate(LocalDate.now()).isActive(true).build());
        return student;
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
