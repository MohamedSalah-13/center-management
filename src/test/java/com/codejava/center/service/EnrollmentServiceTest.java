package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * يغطي منطق الاشتراك بعد إخراجه من الشاشة إلى خدمة معاملاتية.
 * يتحقق أيضاً من الاستعلامات المشتقة (countByGroupAndIsActiveTrue وأخواتها)
 * التي لا يفحصها المُترجم ولا تفشل إلا وقت التشغيل.
 */
@DataJpaTest
// AuditService و UserSession بنسختيهما الحقيقيتين: الاشتراك يكتب في سجل المراقبة
// داخل نفس المعاملة، فاستبدالهما بوهمي كان سيخفي فشل تلك الكتابة عن الاختبار
@Import({EnrollmentService.class, AuditService.class, UserSession.class, SecurityConfig.class})
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
                .schoolLevel(SchoolLevel.PREP1)
                .meetingDays(Set.of(DayOfWeek.SATURDAY))
                .startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(18, 0))
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
                .hasMessage(I18n.get("error.enrollment.alreadyMember"));
    }

    @Test
    void rejectsSubscriptionWhenGroupIsFull() {
        enrollmentService.subscribe(persistStudent("STU-E3", "طالب 3"), group);
        enrollmentService.subscribe(persistStudent("STU-E4", "طالب 4"), group);

        assertThatThrownBy(() -> enrollmentService.subscribe(persistStudent("STU-E5", "طالب 5"), group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.enrollment.groupFull", group.getMaxCapacity()));
    }

    /**
     * القيد الأساسي: المجموعة تخدم صفاً واحداً، ولا يُقبل فيها طالب من صف آخر.
     * القبول هنا كان يعني حصةً كاملة يجلس فيها الطالب أمام منهج ليس منهجه.
     */
    @Test
    void rejectsStudentFromAnotherLevel() {
        Student secondary = studentRepository.saveAndFlush(Student.builder()
                .barcode("STU-E8").name("طالب ثانوي")
                .schoolLevel(SchoolLevel.SEC1).isActive(true).build());

        assertThatThrownBy(() -> enrollmentService.subscribe(secondary, group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.enrollment.levelMismatch",
                        secondary.getName(),
                        SchoolLevel.SEC1.getDisplayName(),
                        SchoolLevel.PREP1.getDisplayName()));

        assertThat(enrollmentService.countActiveMembers(group)).isZero();
    }

    /** طالب بلا مرحلة لا يُقبل بالتخمين: البيانات الناقصة تُستكمل ولا تُتجاوَز */
    @Test
    void rejectsStudentWithoutLevel() {
        Student unknown = studentRepository.saveAndFlush(Student.builder()
                .barcode("STU-E9").name("طالب بلا مرحلة").isActive(true).build());

        assertThatThrownBy(() -> enrollmentService.subscribe(unknown, group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.enrollment.studentLevelMissing", unknown.getName()));
    }

    /**
     * مجموعة أُنشئت قبل هذه الميزة (بلا صف) لا تقبل أحداً حتى يُضبط صفها،
     * وإلا كان الترحيل قد ألغى القيد بصمت عن كل مجموعة قائمة.
     */
    @Test
    void rejectsSubscriptionToGroupWithoutLevel() {
        CourseGroup legacy = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة قديمة").teacher(group.getTeacher())
                .maxCapacity(10).sessionPrice(new BigDecimal("40.00"))
                .build());

        Student student = persistStudent("STU-E10", "طالب");

        assertThatThrownBy(() -> enrollmentService.subscribe(student, legacy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.enrollment.groupLevelMissing", legacy.getName()));
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

    /** إنهاء الاشتراك يثبّت يوم الخروج - وهو ما يحدّ حساب حصص الطالب في المجموعة */
    @Test
    void unsubscribeRecordsLeaveDateAndFreesTheSeat() {
        Student student = persistStudent("STU-E11", "طالب خارج");
        enrollmentService.subscribe(student, group);

        StudentGroup ended = enrollmentService.unsubscribe(student.getId(), group.getId());

        assertThat(ended.isActive()).isFalse();
        assertThat(ended.getLeaveDate()).isEqualTo(LocalDate.now());
        assertThat(enrollmentService.countActiveMembers(group)).isZero();
    }

    @Test
    void unsubscribeRejectsWhenThereIsNoLiveMembership() {
        Student student = persistStudent("STU-E12", "طالب غير مشترك");

        assertThatThrownBy(() -> enrollmentService.unsubscribe(student.getId(), group.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.get("error.enrollment.notMember"));
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

        enrollmentService.unsubscribe(student.getId(), group.getId());
        StudentGroup rejoined = enrollmentService.subscribe(student, group);

        assertThat(rejoined.getId()).isEqualTo(originalId);
        assertThat(rejoined.isActive()).isTrue();
        // تاريخ الخروج القديم يُمسح، وإلا انتهت مدة احتساب حصصه عند خروجه الأول
        assertThat(rejoined.getLeaveDate()).isNull();
        assertThat(studentGroupRepository.count()).isEqualTo(1);
    }

    private Student persistStudent(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name)
                .schoolLevel(SchoolLevel.PREP1)
                .isActive(true).build());
    }
}
