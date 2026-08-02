package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Student;
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
 * استعلام المتأخرات هو أعقد استعلام في المشروع: تجميع بـ CASE داخل SUM
 * مع HAVING وربط entity join. لا يفحصه المُترجم إطلاقاً.
 */
@DataJpaTest
@Import(SecurityConfig.class)
class ArrearsQueryTest {

    @Autowired private StudentRepository studentRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void listsOnlyStudentsWhoseChargesExceedTheirPayments() {
        Student inArrears = persistStudent("STU-A1", "طالب مدين");
        Student settled = persistStudent("STU-A2", "طالب منتظم");

        // مدين: دفع 50 وعليه رسوم 80 => الرصيد -30
        save(inArrears, TransactionType.INCOME, "50.00");
        save(inArrears, TransactionType.SESSION_CHARGE, "80.00");

        // منتظم: دفع 100 وعليه رسوم 40 => الرصيد +60
        save(settled, TransactionType.INCOME, "100.00");
        save(settled, TransactionType.SESSION_CHARGE, "40.00");

        var arrears = studentRepository.findStudentsInArrears(null);

        assertThat(arrears).extracting(StudentBalance::studentName).containsExactly("طالب مدين");
        assertThat(arrears.get(0).balance()).isEqualByComparingTo("-30.00");
        assertThat(arrears.get(0).amountDue()).isEqualByComparingTo("30.00");
    }

    /** الطالب بلا أي حركة رصيده صفر، فلا يُعدّ مديناً */
    @Test
    void studentWithNoTransactionsIsNotInArrears() {
        persistStudent("STU-A3", "طالب جديد");

        assertThat(studentRepository.findStudentsInArrears(null)).isEmpty();
    }

    /** الطالب الموقوف لا يظهر في التقرير */
    @Test
    void inactiveStudentsAreExcluded() {
        Student suspended = studentRepository.saveAndFlush(Student.builder()
                .barcode("STU-A4").name("طالب موقوف").isActive(false).build());
        save(suspended, TransactionType.SESSION_CHARGE, "40.00");

        assertThat(studentRepository.findStudentsInArrears(null)).isEmpty();
    }

    /** الحركات السابقة لبداية دفتر الحسابات لا تُحتسب */
    @Test
    void transactionsBeforeLedgerStartAreIgnored() {
        Student student = persistStudent("STU-A5", "طالب ترحيل");

        Transaction oldCharge = transaction(student, TransactionType.SESSION_CHARGE, "500.00");
        oldCharge.setTransactionDate(LocalDateTime.now().minusDays(10));
        transactionRepository.saveAndFlush(oldCharge);

        assertThat(studentRepository.findStudentsInArrears(LocalDate.now().atStartOfDay())).isEmpty();
        assertThat(studentRepository.findStudentsInArrears(null)).hasSize(1);
    }

    /** الأكثر مديونية أولاً */
    @Test
    void ordersMostIndebtedFirst() {
        Student small = persistStudent("STU-A6", "مدين قليل");
        Student large = persistStudent("STU-A7", "مدين كثير");
        save(small, TransactionType.SESSION_CHARGE, "20.00");
        save(large, TransactionType.SESSION_CHARGE, "90.00");

        assertThat(studentRepository.findStudentsInArrears(null))
                .extracting(StudentBalance::studentName)
                .containsExactly("مدين كثير", "مدين قليل");
    }

    private Student persistStudent(String barcode, String name) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name).phone("0100").parentPhone("0111").isActive(true).build());
    }

    private void save(Student student, TransactionType type, String amount) {
        transactionRepository.saveAndFlush(transaction(student, type, amount));
    }

    private Transaction transaction(Student student, TransactionType type, String amount) {
        return Transaction.builder()
                .type(type).amount(new BigDecimal(amount)).description("اختبار")
                .transactionDate(LocalDateTime.now()).student(student).build();
    }
}
