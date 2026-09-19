package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.AuditLogRepository;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * سلوك المعاملات في سجل المراقبة.
 *
 * <p>هذا هو الاختبار الذي يحمي القرار الوحيد الذي لو انقلب لبدا كل شيء سليماً: نوع
 * الانتشار. لو كُتبت أحداث النجاح في معاملة مستقلة لَبقيت أسطر عن عمليات تراجعت ولم
 * تقع، ولو كُتبت أحداث الرفض داخل معاملة من رفضه لَمُحيت مع الاستثناء الذي يليها -
 * وفي الحالتين يمتلئ السجل ويعمل البرنامج ولا يشكو أحد.</p>
 *
 * <p>الاختبارات التي تفحص هذا السلوك تُعطَّل فيها معاملة الاختبار
 * ({@code NOT_SUPPORTED}) وتبني معاملتها بيدها، وإلا كان الغلاف الذي يلفّ كل اختبار
 * في {@code @DataJpaTest} هو ما يقرّر النتيجة.</p>
 */
@DataJpaTest
@Import({AuditService.class, UserSession.class, SecurityConfig.class})
class AuditServiceTest {

    @Autowired private AuditService auditService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserSession userSession;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * التنظيف يمرّ من خارج المستودع لأن المستودع لا يعلن حذفاً أصلاً - وهو المقصود.
     * الأحداث المكتوبة في معاملة مستقلة تُحفظ فعلاً ولا تتراجع مع الاختبار.
     */
    @AfterEach
    void clearTrail() {
        userSession.cleanUserSession();
        jdbcTemplate.execute("delete from audit_logs");
    }

    @Test
    void stampsTheEventWithTheCurrentUserAndRole() {
        userSession.setCurrentUser(admin("أحمد"));

        auditService.record(AuditAction.STUDENT_CREATED, 7L, "طالب جديد", "barcode=STU-1");

        AuditLog written = all().get(0);
        assertThat(written.getActorUsername()).isEqualTo("أحمد");
        assertThat(written.getActorRole()).isEqualTo(Role.ADMIN);
        assertThat(written.getEntityId()).isEqualTo(7L);
        assertThat(written.isSuccessful()).isTrue();
        assertThat(written.getOccurredAt()).isNotNull();
    }

    /** بلا جلسة: الفاعل هو النظام، لا مستخدم مجهول ولا آخر من سجّل دخوله */
    @Test
    void attributesToTheSystemWhenThereIsNoSession() {
        auditService.record(AuditAction.BACKUP_CREATED, null, "backup_2026-01-01.sql");

        assertThat(all().get(0).getActorUsername()).isNull();
    }

    /**
     * محاولة الوصول المرفوضة تبقى بعد تراجع المعاملة التي رفضتها.
     * هذا هو سبب {@code REQUIRES_NEW}: الاستثناء يلي السطر مباشرةً دائماً.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aDenialSurvivesTheRollbackOfTheOperationThatRaisedIt() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            auditService.recordFailure(AuditAction.ACCESS_DENIED,
                    "TransactionService.recordExpense", "required=ADMIN; actual=SECRETARY");
            throw new IllegalStateException("الرفض يُلغي المعاملة");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(all()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getAction()).isEqualTo(AuditAction.ACCESS_DENIED);
                    assertThat(event.isSuccessful()).isFalse();
                });
    }

    /**
     * والعكس: عملية تراجعت لا تترك سطراً يقول إنها وقعت.
     * سجل يذكر تحصيلاً لا وجود له في جدول الحركات أسوأ من سجل ناقص.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aSuccessEventRollsBackWithTheOperationItDescribes() {
        userSession.setCurrentUser(admin("أحمد"));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            auditService.record(AuditAction.PAYMENT_RECORDED, 1L, "طالب", null, "دفعة");
            throw new IllegalStateException("فشل بعد الكتابة");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(all()).isEmpty();
    }

    /** نصّ أطول من العمود يُقصّ ولا يُفشل العملية التي يصفها */
    @Test
    void clipsOversizedTextInsteadOfFailingTheOperation() {
        userSession.setCurrentUser(admin("أحمد"));

        auditService.record(AuditAction.SETTINGS_UPDATED, 1L, "س".repeat(400), "ت".repeat(900));

        AuditLog written = all().get(0);
        assertThat(written.getEntityLabel()).hasSize(150);
        assertThat(written.getDetails()).hasSize(500);
    }

    private User admin(String username) {
        return User.builder().id(1L).username(username).password("x").role(Role.ADMIN).build();
    }

    private List<AuditLog> all() {
        return auditLogRepository.search(
                LocalDate.now().minusDays(1).atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(),
                null, List.of(AuditAction.values()), PageRequest.of(0, 50));
    }
}
