package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.TransactionRepository;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حدّا يوم الدرج، بساعةٍ مثبَّتة.
 *
 * <p>هذا هو ما فتحه حقنُ {@link Clock}: صافي الدرج يُحسب بين بداية اليوم ونهايته، وحافة
 * منتصف الليل لم يكن لها اختبار لأن {@code LocalDateTime.now()} يقرأ ساعة الجهاز من داخل
 * الدالة — فلا سبيل إلى الوقوف على تلك الحافة إلا بانتظارها.</p>
 *
 * <p>والحافة ليست نظرية: عمود {@code transaction_date} من نوع {@code datetime(6)}، وكان
 * الحدّ الأعلى مكتوباً {@code 23:59:59} — فدفعةٌ يقبضها الكاشير في 23:59:59.4 تسقط من
 * الجرد. الرقم يُطابَق بالنقد في الدرج، ففرقٌ لا يُفسَّر هو ليلةُ عدٍّ كاملة.</p>
 */
@DataJpaTest
@Import({TransactionService.class, SettingsService.class, AuditService.class,
        UserSession.class, SecurityConfig.class, TillDayBoundaryTest.FixedClockConfig.class})
@EnableAspectJAutoProxy
class TillDayBoundaryTest {

    /** ظهرُ يومٍ بعينه: كل ما في الاختبار يُقاس منه */
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 8, 4, 12, 0);

    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserSession userSession;

    @BeforeEach
    void loginAsAdmin() {
        // calculateTodayNetBalance محمية بـ @RequiresRole(ADMIN)
        userSession.setCurrentUser(User.builder()
                .id(1L).username("admin").password("x").role(Role.ADMIN).build());
    }

    @AfterEach
    void logout() {
        userSession.cleanUserSession();
    }

    @Test
    void theLastFractionOfASecondBeforeMidnightIsStillTodaysTill() {
        persistIncome(new BigDecimal("100.00"), NOON);
        persistIncome(new BigDecimal("50.00"), NOON.toLocalDate().atTime(23, 59, 59, 400_000_000));

        assertThat(transactionService.calculateTodayNetBalance())
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }

    /** ودفعةُ أمس ليست في درج اليوم، ولو كانت قبل منتصف الليل بلحظة */
    @Test
    void yesterdaysLastPaymentIsNotInTodaysTill() {
        persistIncome(new BigDecimal("100.00"), NOON);
        persistIncome(new BigDecimal("70.00"),
                NOON.toLocalDate().minusDays(1).atTime(23, 59, 59, 900_000_000));

        assertThat(transactionService.calculateTodayNetBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void expensesAndTeacherPayoutsComeOutOfTheSameDrawer() {
        persistIncome(new BigDecimal("300.00"), NOON);
        persist(TransactionType.EXPENSE, new BigDecimal("40.00"), NOON);
        persist(TransactionType.TEACHER_PAYOUT, new BigDecimal("60.00"), NOON);

        assertThat(transactionService.calculateTodayNetBalance())
                .isEqualByComparingTo(new BigDecimal("200.00"));
    }

    private void persistIncome(BigDecimal amount, LocalDateTime at) {
        persist(TransactionType.INCOME, amount, at);
    }

    private void persist(TransactionType type, BigDecimal amount, LocalDateTime at) {
        transactionRepository.saveAndFlush(Transaction.builder()
                .type(type)
                .amount(amount)
                .transactionDate(at)
                .description("اختبار")
                .build());
    }

    /**
     * ساعة ثابتة على ظهر اليوم نفسه. المنطقة {@code UTC} لا منطقة الجهاز: الاختبار يقارن
     * بقيم {@code LocalDateTime} مكتوبة صراحةً، فمنطقةٌ متغيّرة تجعله ينجح في القاهرة
     * ويفشل في خادم البناء.
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOON.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        }
    }
}
