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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * أرشفة الطالب: المخرج المتاح لمن تخرّج أو انقطع.
 *
 * <p>الحذف تمنعه المفاتيح الأجنبية لكل من له حضور أو حركة مالية، أي لكل من درس
 * فعلاً، فبغير الأرشفة يبقى كل من مرّ بالسنتر في الجدول - وفي كل قراءة تجريها
 * الشاشة - إلى الأبد.</p>
 *
 * <p>ما يُثبَّت هنا هو حدودها: تُخرج الطالب من قراءة الشاشة ومن العدّ، ولا تمسّ
 * اشتراكاته. إنهاء الاشتراك يكتب تاريخ خروج تُحسب عليه حصص الطالب، وجعله أثراً
 * جانبياً لزرٍّ في شاشة أخرى يفسد أرقام حضوره بلا أن يطلب أحد ذلك.</p>
 */
@DataJpaTest
@Import({StudentService.class, SettingsService.class, AuditService.class,
        com.codejava.center.util.UserSession.class, SecurityConfig.class})
class StudentArchivingTest {

    @Autowired private StudentService studentService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    @Test
    void archivedStudentLeavesTheScreenAndTheCountThenComesBack() {
        Student student = persistStudent("STU-ARCH1", "طالب متخرج");

        studentService.setArchived(student.getId(), true);

        assertThat(studentService.getStudents(false)).isEmpty();
        assertThat(studentService.countActiveStudents()).isZero();

        // ولا يختفي من قاعدة البيانات: سجله وحركاته المالية كما هي
        assertThat(studentService.getStudents(true))
                .extracting(Student::getId)
                .containsExactly(student.getId());

        studentService.setArchived(student.getId(), false);

        assertThat(studentService.getStudents(false))
                .extracting(Student::getId)
                .containsExactly(student.getId());
        assertThat(studentService.countActiveStudents()).isEqualTo(1);
    }

    /**
     * الأرشفة لا تُنهي اشتراكاً. الطالب يظل محسوباً في سعة مجموعته حتى يُنهي
     * المستخدم اشتراكه من جدول الاشتراكات - وشاشة التسجيل تقول ذلك قبل التأكيد،
     * لأن أرشفةً تُخلي مقعداً بصمت تفتح المجموعة لطالب جديد بلا قرار من أحد.
     */
    @Test
    void archivingLeavesLiveEnrolmentsUntouched() {
        Student student = persistStudent("STU-ARCH2", "طالب مشترك");
        StudentGroup membership = enrol(student);

        studentService.setArchived(student.getId(), true);

        StudentGroup after = studentGroupRepository.findById(membership.getId()).orElseThrow();
        assertThat(after.isActive()).isTrue();
        assertThat(after.getLeaveDate()).isNull();
    }

    private Student persistStudent(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode)
                .name(name)
                .isActive(true)
                .build());
    }

    private StudentGroup enrol(Student student) {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم " + student.getBarcode())
                .subject("رياضيات")
                .commissionType("PERCENTAGE")
                .commissionValue(new BigDecimal("50.00"))
                .build());

        CourseGroup group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة " + student.getBarcode())
                .teacher(teacher)
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("40.00"))
                .build());

        return studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student)
                .group(group)
                .joinDate(LocalDate.now())
                .isActive(true)
                .build());
    }
}
