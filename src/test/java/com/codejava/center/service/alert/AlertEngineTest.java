package com.codejava.center.service.alert;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Alert;
import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.repository.AlertRuleRepository;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.notification.MessageSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * توجيه المحرّك ومنعه للتكرار.
 *
 * <p>ثلاثة قرارات هنا لو انقلب أيٌّ منها لَعمل البرنامج ولم يشكُ أحد، وهي بالضبط ما
 * يغطّيه هذا الصف: أن التنبيه الواحد لا يُكتب مرتين في نافذة تهدئته، وأن قاعدةً
 * وجهتها داخلية <b>لا تُرسل شيئاً إلى هاتف أحد</b>، وأن رسالة ولي الأمر تبدأ باسم
 * السنتر - فرسالةٌ من رقم مجهول بلا اسم مُرسِل تُقرأ على أنها احتيال.</p>
 *
 * <p>المعاملة معطَّلة في الاختبارات ({@code NOT_SUPPORTED}) لأن {@code AlertWriter}
 * يكتب في معاملة مستقلة: لو لفّها غلاف الاختبار لَما اختبرنا سلوكه بل سلوك الغلاف.
 * ولذلك يُنظَّف الجدول باليد بعد كل اختبار - ما كُتب في معاملة مستقلة لا يتراجع.</p>
 */
@DataJpaTest
@Import({AlertEngine.class, AlertWriter.class, AlertRuleRegistry.class, SecurityConfig.class,
        AlertEngineTest.StubDetectorConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlertEngineTest {

    private static final String CENTER = "سنتر النور";

    @Autowired private AlertEngine alertEngine;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private StubDetector stubDetector;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private NotificationService notificationService;
    @MockBean private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        given(settingsService.getCenterName()).willReturn(CENTER);
        given(notificationService.sendAutomatic(any())).willReturn(MessageSender.SendResult.ok());

        stubDetector.drafts.clear();
        stubDetector.drafts.add(AlertDraft.forStudent(41L, "أحمد محمود", "01012345678",
                "أحمد محمود", "150.00"));
    }

    @AfterEach
    void clearTables() {
        jdbcTemplate.execute("delete from alerts");
        jdbcTemplate.execute("delete from alert_rules");
    }

    /**
     * الحالة التي يوجد القيد الفريد من أجلها: السنتر فيه أكثر من جهاز، وكلٌّ منها
     * يشغّل مجدوِله فيصحو ثلاثتها على موعد الفحص نفسه.
     */
    @Test
    void theSameConditionRaisesOneAlertNoMatterHowManyScansRun() {
        persistRule(AlertAudience.INTERNAL, 7);

        alertEngine.scanAll();
        alertEngine.scanAll();
        alertEngine.scanAll();

        assertThat(alertRepository.findAll()).hasSize(1);
    }

    @Test
    void aRaisedAlertCarriesItsArgumentsAndNotATranslatedSentence() {
        persistRule(AlertAudience.INTERNAL, 7);

        alertEngine.scanAll();

        Alert raised = alertRepository.findAll().get(0);
        assertThat(raised.getType()).isEqualTo(AlertType.ARREARS);
        assertThat(raised.getEntityId()).isEqualTo(41L);
        assertThat(raised.getEntityLabel()).isEqualTo("أحمد محمود");
        assertThat(raised.getArgs()).isEqualTo("أحمد محمود|150.00");
        assertThat(raised.isAcknowledged()).isFalse();
    }

    /**
     * الوجهة الداخلية تعني ألا يخرج شيء من السنتر. انقلاب هذا الشرط يعني رسائل تصل
     * أرقاماً حقيقية دون أن يطلبها أحد، وهو أسوأ ما يمكن أن يفعله هذا النظام.
     */
    @Test
    void anInternalRuleNeverMessagesAnybody() {
        persistRule(AlertAudience.INTERNAL, 7);

        alertEngine.scanAll();

        verify(notificationService, never()).sendAutomatic(any());
    }

    /** ولا يُرسل نوعٌ غير صالح للإرسال ولو حُفظت له وجهة خارجية بطريقة ما */
    @Test
    void aTypeThatCannotReachParentsIsNeverSentEvenWhenTheStoredAudienceSaysOtherwise() {
        assertThat(AlertType.BACKUP_FAILED.isParentCapable()).isFalse();

        AlertRule stored = AlertRule.defaultsFor(AlertType.BACKUP_FAILED);
        stored.setAudience(AlertAudience.BOTH);

        assertThat(stored.notifiesParents()).isFalse();
    }

    @Test
    void aParentRuleSendsTheMessageWithTheCentreNameFirst() {
        persistRule(AlertAudience.BOTH, 7);

        alertEngine.scanAll();

        ArgumentCaptor<NotificationCandidate> sent = ArgumentCaptor.forClass(NotificationCandidate.class);
        verify(notificationService).sendAutomatic(sent.capture());

        NotificationCandidate candidate = sent.getValue();
        assertThat(candidate.type()).isEqualTo(AlertType.ARREARS);
        assertThat(candidate.studentId()).isEqualTo(41L);
        assertThat(candidate.phoneValid()).isTrue();
        // اسم السنتر في الموضع 0 من كل قالب يخصّ أولياء الأمور - قاعدة ثابتة بلا استثناء
        assertThat(candidate.message()).contains(CENTER).contains("أحمد محمود").contains("150.00");
    }

    /** قاعدة موقوفة لا تُفحص أصلاً: الإيقاف يجب أن يعني الصمت التام لا التقليل */
    @Test
    void aDisabledRuleRaisesNothing() {
        AlertRule rule = AlertRule.defaultsFor(AlertType.ARREARS);
        rule.setEnabled(false);
        alertRuleRepository.saveAndFlush(rule);

        alertEngine.scanAll();

        assertThat(alertRepository.findAll()).isEmpty();
        verify(notificationService, never()).sendAutomatic(any());
    }

    private void persistRule(AlertAudience audience, int cooldownDays) {
        AlertRule rule = AlertRule.defaultsFor(AlertType.ARREARS);
        rule.setAudience(audience);
        rule.setCooldownDays(cooldownDays);
        alertRuleRepository.saveAndFlush(rule);
    }

    /**
     * فاحص واحد يكفي: المقصود سلوك المحرّك لا صحّة استعلام فاحص بعينه، وتلك يغطّيها
     * {@code RepositoryQueryValidationTest}. وهو ضروري لا اختياري - {@code AlertEngine}
     * يحقن {@code List<AlertDetector>} وقائمةٌ بلا مرشّح واحد تُفشل بناء السياق.
     */
    @TestConfiguration
    static class StubDetectorConfig {
        @Bean
        StubDetector stubDetector() {
            return new StubDetector();
        }
    }

    static class StubDetector implements AlertDetector {

        private final List<AlertDraft> drafts = new ArrayList<>();

        @Override
        public AlertType type() {
            return AlertType.ARREARS;
        }

        @Override
        public List<AlertDraft> detect(AlertRule rule) {
            return List.copyOf(drafts);
        }
    }
}
