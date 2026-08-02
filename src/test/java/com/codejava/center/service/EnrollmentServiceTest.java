package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * يغطي منطق الاشتراك بعد إخراجه من الشاشة إلى خدمة معاملاتية.
 * يتحقق أيضاً من الاستعلامات المشتقة (countByGroupAndIsActiveTrue وأخواتها)
 * التي لا يفحصها المُترجم ولا تفشل إلا وقت التشغيل.
 */
@DataJpaTest
@Import({EnrollmentService.class, SecurityConfig.class})
class EnrollmentServiceTest {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    private CourseGroup group;

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());

        group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة أ").teacher(teacher)
                .maxCapacity(2).sessionPrice(new BigDecimal("50.00"))
                .build());
    }

    @Test
    void subscribesStudentAndCountsThemAsActiveMember() {
        Student student = persistStudent("STU-E1", "طالب أول");

        enrollmentService.subscribe(student, group);

        assertThat(enrollmentService.countActiveMembers(group)).isEqualTo(1);
        assertThat(studentGroupRepository.existsByStudentAndGroupAndIsActiveTrue(student, group)).isTrue();
    }

    @Test
    void rejectsDuplicateActiveSubscription() {
        Student student = persistStudent("STU-E2", "طالب ثانٍ");
        enrollmentService.subscribe(student, group);

        assertThatThrownBy(() -> enrollmentService.subscribe(student, group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("مشترك بالفعل");
    }

    @Test
    void rejectsSubscriptionWhenGroupIsFull() {
        enrollmentService.subscribe(persistStudent("STU-E3", "طالب 3"), group);
        enrollmentService.subscribe(persistStudent("STU-E4", "طالب 4"), group);

        assertThatThrownBy(() -> enrollmentService.subscribe(persistStudent("STU-E5", "طالب 5"), group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("مكتملة العدد");
    }

    /**
     * الاشتراك الملغى يجب ألا يشغل مقعداً في المجموعة.
     * قبل الإصلاح كانت countByGroup تعدّه فتمتلئ السعة بطلاب انسحبوا.
     */
    @Test
    void cancelledSubscriptionDoesNotOccupyCapacity() {
        Student left = persistStudent("STU-E6", "طالب منسحب");
        StudentGroup membership = enrollmentService.subscribe(left, group);

        membership.setActive(false);
        studentGroupRepository.saveAndFlush(membership);

        assertThat(enrollmentService.countActiveMembers(group)).isZero();
        assertThat(studentGroupRepository.existsByStudentAndGroupAndIsActiveTrue(left, group)).isFalse();
    }

    /**
     * الطالب الذي انسحب يجب أن يستطيع العودة، وأن تُعاد تفعيل عضويته
     * بدل إنشاء صف مكرر لنفس الطالب ونفس المجموعة.
     */
    @Test
    void reactivatesPreviousMembershipInsteadOfCreatingDuplicate() {
        Student student = persistStudent("STU-E7", "طالب عائد");
        StudentGroup first = enrollmentService.subscribe(student, group);
        Long originalId = first.getId();

        first.setActive(false);
        studentGroupRepository.saveAndFlush(first);

        StudentGroup rejoined = enrollmentService.subscribe(student, group);

        assertThat(rejoined.getId()).isEqualTo(originalId);
        assertThat(rejoined.isActive()).isTrue();
        assertThat(studentGroupRepository.count()).isEqualTo(1);
    }

    private Student persistStudent(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name).isActive(true).build());
    }
}
