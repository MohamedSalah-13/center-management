package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.AuditCategory;
import com.codejava.center.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * استعلامات سجل المراقبة.
 *
 * <p>لا يفحص المُترجم منها شيئاً: شرط {@code :actor IS NULL OR ...} وشرط {@code IN}
 * على قائمة enum والترتيب المركَّب كلها تُحلّ وقت التشغيل. وخطؤها هنا لا يظهر كعطل
 * بل كسجل ناقص - وهو ما لا يلاحظه أحد إلا حين يحتاجه.</p>
 */
@DataJpaTest
@Import(SecurityConfig.class)
class AuditLogQueryTest {

    @Autowired private AuditLogRepository auditLogRepository;

    private static final List<AuditAction> ALL = List.of(AuditAction.values());

    @Test
    void returnsOnlyEventsInsideThePeriod() {
        save(AuditAction.PAYMENT_RECORDED, "محصّل", LocalDateTime.now().minusDays(10), "قديم");
        save(AuditAction.PAYMENT_RECORDED, "محصّل", LocalDateTime.now(), "اليوم");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, ALL))
                .extracting(AuditLog::getEntityLabel).containsExactly("اليوم");
    }

    /** آخر ثانية من اليوم الأخير داخل الفترة: المقارنة ببداية اليوم التالي لا بمنتصف ليلته */
    @Test
    void includesTheLastInstantOfTheClosingDay() {
        save(AuditAction.EXPENSE_RECORDED, "أمين", LocalDate.now().atTime(23, 59, 59, 999_000_000), "آخر لحظة");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, ALL)).hasSize(1);
    }

    @Test
    void filtersByActorWhenOneIsGiven() {
        save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "أ");
        save(AuditAction.PAYMENT_RECORDED, "سعاد", LocalDateTime.now(), "س");

        assertThat(search(LocalDate.now(), LocalDate.now(), "سعاد", ALL))
                .extracting(AuditLog::getEntityLabel).containsExactly("س");
    }

    /** تمرير null في اسم المستخدم يعني الجميع، لا "من لا اسم له" */
    @Test
    void nullActorMeansEveryone() {
        save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "أ");
        save(AuditAction.BACKUP_CREATED, null, LocalDateTime.now(), "نسخة النظام");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, ALL)).hasSize(2);
    }

    @Test
    void filtersByCategoryThroughItsActions() {
        save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "مالي");
        save(AuditAction.STUDENT_DELETED, "أحمد", LocalDateTime.now(), "بيانات");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, AuditAction.of(AuditCategory.FINANCE)))
                .extracting(AuditLog::getEntityLabel).containsExactly("مالي");
    }

    /**
     * الأحدث أولاً، وعند تساوي اللحظة يفصل المعرّف.
     * بدون الفاصل الثاني كان ترتيب حدثين في نفس الجزء من الثانية يتغيّر بين تحديث وآخر.
     */
    @Test
    void ordersNewestFirstAndBreaksTiesByIdentifier() {
        LocalDateTime sameMoment = LocalDateTime.now().withNano(0);
        save(AuditAction.SESSION_OPENED, "أحمد", sameMoment, "الأول");
        save(AuditAction.SESSION_CLOSED, "أحمد", sameMoment, "الثاني");
        save(AuditAction.STUDENT_CREATED, "أحمد", sameMoment.plusMinutes(1), "الأحدث");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, ALL))
                .extracting(AuditLog::getEntityLabel)
                .containsExactly("الأحدث", "الثاني", "الأول");
    }

    /** العدد الكلي يتجاهل سقف الصفحة: هو ما يخبر الشاشة أن ما تعرضه مقصوص */
    @Test
    void countIgnoresThePageLimit() {
        for (int i = 0; i < 5; i++) {
            save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "حدث " + i);
        }

        List<AuditLog> firstTwo = auditLogRepository.search(
                LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay(),
                null, ALL, PageRequest.of(0, 2));

        assertThat(firstTwo).hasSize(2);
        assertThat(auditLogRepository.countMatching(
                LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay(), null, ALL))
                .isEqualTo(5);
    }

    /** أسماء الفاعلين تُقرأ من السجل نفسه، والنظام (بلا اسم) لا يظهر بينها */
    @Test
    void listsDistinctActorsWithoutTheSystem() {
        save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "أ");
        save(AuditAction.EXPENSE_RECORDED, "أحمد", LocalDateTime.now(), "أ2");
        save(AuditAction.BACKUP_CREATED, null, LocalDateTime.now(), "نسخة");

        assertThat(auditLogRepository.findDistinctActors()).containsExactly("أحمد");
    }

    /** المبلغ يعبر إلى قاعدة البيانات ويعود بخانتيه، كبقية أموال النظام */
    @Test
    void keepsTheMoneyScaleOfFinancialEvents() {
        save(AuditAction.PAYMENT_RECORDED, "أحمد", LocalDateTime.now(), "دفعة");

        assertThat(search(LocalDate.now(), LocalDate.now(), null, ALL).get(0).getAmount())
                .isEqualByComparingTo("150.50");
    }

    private List<AuditLog> search(LocalDate from, LocalDate to, String actor, List<AuditAction> actions) {
        return auditLogRepository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                actor, actions, PageRequest.of(0, 100));
    }

    private void save(AuditAction action, String actor, LocalDateTime moment, String label) {
        auditLogRepository.save(AuditLog.builder()
                .occurredAt(moment)
                .actorUsername(actor)
                .actorRole(actor == null ? null : Role.ADMIN)
                .action(action)
                .entityLabel(label)
                .amount(new BigDecimal("150.50"))
                .successful(true)
                .build());
    }
}
