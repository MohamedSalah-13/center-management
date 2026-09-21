package com.codejava.center.service;

import com.codejava.center.TestActor;
import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.dto.StudentDraft;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ما تضمنه {@link StudentDraft} حين يصير مدخلَ الحفظ بدل الكيان.
 *
 * <p>كلُّ ما هنا فشلٌ صامت: لا يرمي شيئاً، ولا يُرى في الشاشة، ويُقرأ بعد أسابيع على
 * أنه سلوك البرنامج. مؤرشفٌ عاد إلى بوابة الحضور لأن أحداً عدّل رقم هاتفه، وبطاقةٌ
 * مطبوعة بطلت لأن حقل الباركود تُرك فارغاً، وصفٌّ جديد كان يُكتب لمعرّفٍ لا وجود له.</p>
 */
@DataJpaTest
@Import({StudentService.class, SettingsService.class, AuditService.class,
        TestActor.class, SecurityConfig.class})
class StudentDraftTest {

    @Autowired private StudentService studentService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TestEntityManager entityManager;

    /**
     * هذا هو سبب غياب {@code isActive} عن المسودة.
     *
     * <p>الأرشفة قرارٌ له بابُه وسطرُه في سجل المراقبة، وتعديلُ رقم هاتفٍ ليس ذلك
     * القرار. الحقلُ غيرُ موجود، فالحفظ لا يملك قلبَه ولو أراد.</p>
     */
    @Test
    void editingAnArchivedStudentLeavesThemArchived() {
        Student student = persist("STU-D1", "طالب مؤرشف");
        studentService.setArchived(student.getId(), true);

        studentService.saveStudent(new StudentDraft(student.getId(), "STU-D1", "طالب مؤرشف",
                "01000000001", "01000000002", SchoolLevel.PREP1));

        assertThat(studentRepository.findById(student.getId()))
                .get()
                .extracting(Student::isActive)
                .isEqualTo(false);
    }

    /** والجديد مسجَّل: لا أحد يُسجَّل مؤرشفاً */
    @Test
    void aNewStudentIsRegistered() {
        Student saved = studentService.saveStudent(
                new StudentDraft(null, null, "طالب جديد", null, null, SchoolLevel.SEC1));

        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getBarcode()).startsWith("STU-");
    }

    /**
     * باركودٌ فارغ في تعديلٍ يعني "اتركه".
     *
     * <p>كان يعني "أصدِر غيره": باركودٌ جديد يعني بطاقةً في يد صاحبها لا تفتح البوابة،
     * ولا شيء على الشاشة يقول إن ذلك ما وقع.</p>
     */
    @Test
    void aBlankBarcodeOnAnEditKeepsTheStoredOne() {
        Student student = persist("STU-D2", "طالب له بطاقة");

        Student saved = studentService.saveStudent(new StudentDraft(student.getId(), "  ",
                "طالب له بطاقة", null, null, null));

        assertThat(saved.getBarcode()).isEqualTo("STU-D2");
    }

    /** معرّفٌ لا وجود له يُرفض، ولا يُكتب صفٌّ جديد مكانه */
    @Test
    void anUnknownIdIsRefusedRatherThanCreatingARow() {
        assertThatThrownBy(() -> studentService.saveStudent(
                new StudentDraft(4242L, null, "طالب وهمي", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.get("error.student.notFound"));

        assertThat(studentRepository.count()).isZero();
    }

    /**
     * التعديل صار يستطيع أن يأخذ باركود طالبٍ آخر أو اسمَه.
     *
     * <p>لم يكن يستطيع حين كان المدخل كياناً تملؤه الشاشة من الصف نفسه، فكان الفحص
     * مقصوراً على الجديد. وبغير هذا يردّه قيدُ القاعدة برسالةٍ فيها اسم الجدول
     * والعمود - وهي ما يقرؤه من يقف أمام الشاشة.</p>
     */
    @Test
    void takingAnotherStudentsBarcodeIsRefused() {
        Student first = persist("STU-D3", "طالب أول");
        Student second = persist("STU-D4", "طالب ثانٍ");

        assertThatThrownBy(() -> studentService.saveStudent(new StudentDraft(second.getId(),
                first.getBarcode(), "طالب ثانٍ", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.get("error.student.barcodeTaken"));
    }

    @Test
    void takingAnotherStudentsNameIsRefused() {
        Student first = persist("STU-D5", "طالب أول");
        Student second = persist("STU-D6", "طالب ثانٍ");

        assertThatThrownBy(() -> studentService.saveStudent(new StudentDraft(second.getId(),
                second.getBarcode(), first.getName(), null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.get("error.student.nameTaken"));
    }

    /** والطالب لا يتعارض مع نسخته المحفوظة: فحصُ ما لم يتغير يمنع كل تعديل */
    @Test
    void savingAStudentWithoutChangingTheirNameOrBarcodeIsAllowed() {
        Student student = persist("STU-D7", "طالب قائم");

        assertThatCode(() -> studentService.saveStudent(new StudentDraft(student.getId(),
                "STU-D7", "طالب قائم", "01000000003", null, SchoolLevel.PREP2)))
                .doesNotThrowAnyException();

        assertThat(studentRepository.findById(student.getId()))
                .get()
                .extracting(Student::getPhone)
                .isEqualTo("01000000003");
    }

    /** الاسم يُحفظ منزوعَ المسافات: مسافةٌ في الطرف تجعل طالبين مختلفين بالاسم نفسه */
    @Test
    void theNameIsStoredTrimmed() {
        Student saved = studentService.saveStudent(
                new StudentDraft(null, null, "  طالب بمسافات  ", null, null, null));

        assertThat(saved.getName()).isEqualTo("طالب بمسافات");
    }

    private Student persist(String barcode, String name) {
        Student student = studentRepository.saveAndFlush(
                Student.builder().barcode(barcode).name(name).build());
        entityManager.clear();
        return student;
    }
}
