package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.*;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * يغطي استعلامات شاشتَي صرف المستحقات وتقفيل الوردية.
 * الخدمات نفسها محمية بـ @RequiresRole فلا يمكن استدعاؤها بلا جلسة داخل شريحة JPA،
 * لذلك يختبر هذا الصف طبقة الـ Repository التي تبني عليها الشاشتان.
 */
@DataJpaTest
@Import(SecurityConfig.class)
class PayoutAndShiftTest {

    @Autowired private SessionRepository sessionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    /**
     * شاشة الصرف تعرض الحصص المغلقة غير المصروفة فقط.
     * الحصة المفتوحة مستبعدة لأن حضورها لم يكتمل، والمصروفة مستبعدة لمنع التكرار.
     */
    @Test
    void payableSessionsExcludeOpenAndAlreadyPaidSessions() {
        CourseGroup group = persistGroup();

        Session payable = persistSession(group, LocalDate.now().minusDays(1), false, false);
        persistSession(group, LocalDate.now().minusDays(2), true, false);   // ما زالت مفتوحة
        persistSession(group, LocalDate.now().minusDays(3), false, true);   // صُرفت بالفعل

        assertThat(sessionRepository.findPayableSessions())
                .extracting(Session::getId)
                .containsExactly(payable.getId());
    }

    /**
     * جرد الوردية حركة نقدية: رسوم الحصص (SESSION_CHARGE) استحقاق على الطالب
     * ولا تدخل الدرج، فوجودها في القائمة يفسد التقفيل.
     */
    @Test
    void cashMovementsExcludeSessionCharges() {
        transactionRepository.saveAndFlush(transaction(TransactionType.INCOME, "100.00"));
        transactionRepository.saveAndFlush(transaction(TransactionType.EXPENSE, "30.00"));
        transactionRepository.saveAndFlush(transaction(TransactionType.TEACHER_PAYOUT, "50.00"));
        transactionRepository.saveAndFlush(transaction(TransactionType.SESSION_CHARGE, "25.00"));

        LocalDate today = LocalDate.now();
        var movements = transactionRepository.findCashMovements(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        assertThat(movements).hasSize(3);
        assertThat(movements).extracting(Transaction::getType)
                .doesNotContain(TransactionType.SESSION_CHARGE);
    }

    /**
     * حدّ نهاية اليوم: حركة الساعة 23:59:59 وبعض الأجزاء كانت تسقط من الجرد
     * حين كانت المقارنة <= 23:59:59 بدل < بداية اليوم التالي.
     */
    @Test
    void shiftRangeIncludesTheFinalSecondOfTheDay() {
        Transaction lateNight = transaction(TransactionType.INCOME, "70.00");
        lateNight.setTransactionDate(LocalDate.now().atTime(23, 59, 59, 999_000_000));
        transactionRepository.saveAndFlush(lateNight);

        LocalDate today = LocalDate.now();
        BigDecimal total = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.INCOME, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        assertThat(total).isEqualByComparingTo("70.00");
    }

    private CourseGroup persistGroup() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلم").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());

        return courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة").teacher(teacher)
                .maxCapacity(30).sessionPrice(new BigDecimal("50.00"))
                .build());
    }

    private Session persistSession(CourseGroup group, LocalDate date, boolean active, boolean paidOut) {
        return sessionRepository.saveAndFlush(Session.builder()
                .group(group).sessionDate(date).isActive(active).isPaidOut(paidOut).build());
    }

    private Transaction transaction(TransactionType type, String amount) {
        return Transaction.builder()
                .type(type).amount(new BigDecimal(amount))
                .description("اختبار").transactionDate(LocalDateTime.now())
                .build();
    }
}
