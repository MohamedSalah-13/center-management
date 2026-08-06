package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.service.dto.StudentBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * يتحقق من أن كل استعلامات @Query المكتوبة يدوياً صحيحة نحوياً وقابلة للتنفيذ.
 * هذه الاستعلامات لا يفحصها المُترجم إطلاقاً، وأي خطأ فيها لا يظهر إلا وقت التشغيل.
 * الاختبار يعمل على قاعدة H2 في الذاكرة ولا يلمس قاعدة بيانات حقيقية.
 */
@DataJpaTest
// CenterApplication نفسه bean يحقن PasswordEncoder، وشريحة @DataJpaTest لا تحمّل SecurityConfig
@Import(SecurityConfig.class)
class RepositoryQueryValidationTest {

    @Autowired private SessionRepository sessionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;

    @Test
    void sessionQueriesAreValid() {
        assertThat(sessionRepository.findAll()).isEmpty();
        assertThat(sessionRepository.findAllActive()).isEmpty();
        assertThat(sessionRepository.findActiveByDate(LocalDate.now())).isEmpty();
        assertThat(sessionRepository.findByIdWithGroup(1L)).isEmpty();

        // التصفية بمعيارين اختياريين: كل تركيبة من (تاريخ/الكل) و(مفتوحة/مغلقة/الكل)
        // تُبنى منها جملة SQL مختلفة، وأيّها قد يفشل وحده
        assertThat(sessionRepository.findFiltered(null, null)).isEmpty();
        assertThat(sessionRepository.findFiltered(LocalDate.now(), null)).isEmpty();
        assertThat(sessionRepository.findFiltered(null, true)).isEmpty();
        assertThat(sessionRepository.findFiltered(LocalDate.now(), false)).isEmpty();

        Student student = persistStudent("STU-SESS01", "طالب حصص");
        assertThat(sessionRepository.findActiveForStudent(student, LocalDate.now())).isEmpty();
    }

    @Test
    void studentBalanceQueryIsValid() {
        Student student = persistStudent("STU-BAL01", "طالب رصيد");

        // بلا حركات: الرصيد صفر وليس null
        assertThat(transactionRepository.calculateStudentBalance(student.getId(), null))
                .isEqualByComparingTo("0");
        assertThat(transactionRepository.calculateStudentBalance(
                student.getId(), LocalDate.now().atStartOfDay())).isEqualByComparingTo("0");
    }

    /**
     * الرصيد = مجموع المدفوع ناقص مجموع رسوم الحصص المخصومة.
     * هذا هو جوهر نموذج المحفظة الذي تعتمد عليه بوابة الحضور.
     */
    @Test
    void balanceSubtractsSessionChargesFromPayments() {
        Student student = persistStudent("STU-BAL02", "طالب محفظة");

        transactionRepository.saveAndFlush(transaction(student, TransactionType.INCOME, "100.00"));
        transactionRepository.saveAndFlush(transaction(student, TransactionType.SESSION_CHARGE, "30.00"));
        transactionRepository.saveAndFlush(transaction(student, TransactionType.SESSION_CHARGE, "30.00"));

        assertThat(transactionRepository.calculateStudentBalance(student.getId(), null))
                .isEqualByComparingTo("40.00");
    }

    /**
     * الحركات السابقة لتاريخ بداية دفتر الحسابات تُستبعد،
     * وهو ما يمنع ظهور الطلاب دائنين بمبالغ وهمية بعد الترحيل.
     */
    @Test
    void balanceIgnoresTransactionsBeforeLedgerStart() {
        Student student = persistStudent("STU-BAL03", "طالب ترحيل");

        Transaction old = transaction(student, TransactionType.INCOME, "500.00");
        old.setTransactionDate(LocalDateTime.now().minusDays(10));
        transactionRepository.saveAndFlush(old);

        transactionRepository.saveAndFlush(transaction(student, TransactionType.INCOME, "70.00"));

        LocalDateTime ledgerStart = LocalDate.now().atStartOfDay();
        assertThat(transactionRepository.calculateStudentBalance(student.getId(), ledgerStart))
                .isEqualByComparingTo("70.00");
    }

    /**
     * استعلامات شاشة التسجيل: المسجَّلون وحدهم، والجميع، والعدّ.
     *
     * <p>مكتوبة {@code @Query} صريحة لا مشتقّة من أسماء الدوال، لأن الحقل
     * {@code isActive} وقارئه {@code isActive()} - وهو موضع فشل اشتقاق الخصائص
     * الصامت. والفشل هنا غير مرئي بنوع أخطر: شاشة تعرض كل من مرّ بالسنتر بدل
     * طلاب العام الجاري تعمل، وتبطؤ سنةً بعد سنة بلا أن يُنسب البطء إلى سببه.</p>
     */
    @Test
    void studentRegisterQueriesSeparateArchivedFromActive() {
        Student current = persistStudent("STU-ACT01", "طالب مسجَّل");

        Student archived = persistStudent("STU-ARC01", "طالب مؤرشف");
        archived.setActive(false);
        studentRepository.saveAndFlush(archived);

        assertThat(studentRepository.findActive())
                .extracting(Student::getId)
                .containsExactly(current.getId());

        // المؤرشف موجود، وآخراً: من يطلب رؤيته يريد الحاليين أمام عينه أولاً
        assertThat(studentRepository.findAllOrdered())
                .extracting(Student::getId)
                .containsExactly(current.getId(), archived.getId());

        assertThat(studentRepository.countActive()).isEqualTo(1);
    }

    private Student persistStudent(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode)
                .name(name)
                .isActive(true)
                .build());
    }

    private Transaction transaction(Student student, TransactionType type, String amount) {
        return Transaction.builder()
                .type(type)
                .amount(new BigDecimal(amount))
                .description("اختبار")
                .transactionDate(LocalDateTime.now())
                .student(student)
                .build();
    }

    /**
     * استعلامات فاحصي التنبيهات. كلّها {@code @Query} مكتوبة يدوياً لا يفحصها المترجم،
     * وخطأٌ في أيٍّ منها لا يظهر إلا في خيط المجدوِل ليلاً حيث لا يراه أحد - فيبدو أن
     * النظام يعمل ولا تنبيه واحد يصل.
     */
    @Test
    void alertDetectorQueriesAreValid() {
        assertThat(studentRepository.findInactiveSince(LocalDate.now().minusDays(14))).isEmpty();
        assertThat(studentRepository.findStudentsWithLowBalance(null, new BigDecimal("50")))
                .isEmpty();
        assertThat(sessionRepository.findOpenSessionsBefore(LocalDate.now())).isEmpty();
        assertThat(sessionRepository.findGroupsWithoutSessionSince(LocalDate.now().minusDays(14)))
                .isEmpty();
    }

    /**
     * الرصيد الموجب الصغير يخصّ {@code LOW_BALANCE} والسالب يخصّ {@code ARREARS}؛ لو
     * تداخل الشرطان لَوصل ولي الأمر رسالتان متناقضتان عن الطالب نفسه في يوم واحد.
     */
    @Test
    void lowBalanceAndArrearsNeverSelectTheSameStudent() {
        Student running = persistStudent("STU-LOW01", "طالب رصيده يقلّ");
        enrol(running);
        transactionRepository.saveAndFlush(transaction(running, TransactionType.INCOME, "100.00"));
        transactionRepository.saveAndFlush(transaction(running, TransactionType.SESSION_CHARGE, "80.00"));

        Student owing = persistStudent("STU-LOW02", "طالب عليه متأخرات");
        enrol(owing);
        transactionRepository.saveAndFlush(transaction(owing, TransactionType.SESSION_CHARGE, "60.00"));

        assertThat(studentRepository.findStudentsWithLowBalance(null, new BigDecimal("50")))
                .extracting(StudentBalance::studentId)
                .containsExactly(running.getId());

        assertThat(studentRepository.findStudentsInArrears(null))
                .extracting(StudentBalance::studentId)
                .containsExactly(owing.getId());
    }

    /** طالب بلا اشتراك سارٍ رصيده صفر أبداً؛ إدراجه يملأ التنبيه بأسماء لا مشكلة فيها */
    @Test
    void lowBalanceIgnoresStudentsWithNoLiveEnrolment() {
        persistStudent("STU-LOW03", "طالب بلا مجموعة");

        assertThat(studentRepository.findStudentsWithLowBalance(null, new BigDecimal("50"))).isEmpty();
    }

    private void enrol(Student student) {
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

        studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student)
                .group(group)
                .joinDate(LocalDate.now())
                .isActive(true)
                .build());
    }

    @Test
    void transactionQueriesAreValid() {
        assertThat(transactionRepository.findByStudentIdOrderByTransactionDateDesc(1L)).isEmpty();
        assertThat(transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.INCOME,
                LocalDate.now().atStartOfDay(),
                LocalDate.now().atTime(23, 59, 59))).isNotNull();
        assertThat(transactionRepository.sumIncomeByGroup(
                LocalDateTime.now().minusDays(30), LocalDateTime.now())).isEmpty();
    }

    @Test
    void attendanceQueriesAreValid() {
        assertThat(attendanceRepository.countAttendancePerDay(LocalDate.now().minusDays(6))).isEmpty();
    }

    @Test
    void courseGroupAndStudentGroupQueriesAreValid() {
        assertThat(courseGroupRepository.findAll()).isEmpty();

        // الطالب يجب أن يكون محفوظاً: Hibernate يعمل flush قبل تنفيذ الاستعلام
        Student student = persistStudent("STU-TEST01", "طالب اختبار");
        assertThat(studentGroupRepository.findByStudentAndIsActiveTrue(student)).isEmpty();
    }

    /**
     * يتأكد أن المبالغ تُخزَّن وتُقرأ كـ BigDecimal بخانتين عشريتين دون انحراف الفاصلة العائمة،
     * وهو الغرض الأساسي من تحويل الأعمدة من Double إلى DECIMAL(12,2).
     */
    @Test
    void moneyIsPersistedWithExactScale() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم اختبار")
                .subject("رياضيات")
                .commissionType("PERCENTAGE")
                .commissionValue(new BigDecimal("50.00"))
                .build());

        CourseGroup saved = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة اختبار")
                .teacher(teacher)
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("33.33"))
                .build());

        assertThat(saved.getSessionPrice()).isEqualByComparingTo("33.33");
        // ثلاث حصص بسعر 33.33 = 99.99 بالضبط، وليس 99.99000000000001
        assertThat(saved.getSessionPrice().multiply(BigDecimal.valueOf(3)))
                .isEqualByComparingTo("99.99");
    }
}
