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
    @Autowired private EndingSoonStubDetector endingSoonDetector;
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

        endingSoonDetector.armed = false;
        endingSoonDetector.occurrenceKey = "2026-08-06";
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

    // ------------------------------------------- الوقائع المسمّاة والنبضة القصيرة

    /**
     * أهمّ اختبار في هذه المجموعة: النبضة القصيرة تسأل الفاحص كل خمس دقائق، والحصة
     * تبقى مفتوحة ساعتين. بلا مفتاح الواقعة يعني ذلك أربعاً وعشرين بطاقة عن الحصة
     * نفسها - أي أن الميزة كلها تصير مصدر إزعاج يُطفئه المستخدم في أول يوم.
     */
    @Test
    void anOccurrenceIsRaisedOnceNoMatterHowOftenItIsScanned() {
        alertRuleRepository.saveAndFlush(AlertRule.defaultsFor(AlertType.SESSION_ENDING_SOON));
        endingSoonDetector.armed = true;

        for (int tick = 0; tick < 12; tick++) {
            alertEngine.scanFrequent();
        }

        assertThat(alertRepository.findAll())
                .extracting(Alert::getType)
                .containsExactly(AlertType.SESSION_ENDING_SOON);
    }

    /**
     * الواقعة التالية تُنبَّه رغم التهدئة. مجموعةٌ تجتمع يومين متتاليين في نفس الساعة
     * يفصل بين موعديها أربعٌ وعشرون ساعة بالضبط، ونافذة تهدئة يوم واحد كانت تبتلع
     * الثاني على حافّة الحساب - غيابٌ لا يشتكي منه أحد لأنه غير مرئي.
     */
    @Test
    void theNextOccurrenceIsRaisedEvenWithinTheCooldownWindow() {
        alertRuleRepository.saveAndFlush(AlertRule.defaultsFor(AlertType.SESSION_ENDING_SOON));
        endingSoonDetector.armed = true;

        alertEngine.scanFrequent();
        endingSoonDetector.occurrenceKey = "2026-08-07"; // اليوم التالي، نفس الحصة والساعة
        alertEngine.scanFrequent();

        assertThat(alertRepository.findAll()).hasSize(2);
    }

    /** النبضة القصيرة لا تلمس الأنواع اليومية: فحصها كل خمس دقائق استعلام بلا فائدة */
    @Test
    void theFrequentScanSkipsDailyTypes() {
        persistRule(AlertAudience.INTERNAL, 7); // ARREARS، ووتيرتها يومية

        alertEngine.scanFrequent();

        assertThat(alertRepository.findAll())
                .extracting(Alert::getType)
                .doesNotContain(AlertType.ARREARS);
    }

    /** والفحص الكامل - اليدوي منه والمجدول - يشمل الاثنين */
    @Test
    void theFullScanCoversEveryCadence() {
        persistRule(AlertAudience.INTERNAL, 7);
        alertRuleRepository.saveAndFlush(AlertRule.defaultsFor(AlertType.SESSION_ENDING_SOON));
        endingSoonDetector.armed = true;

        alertEngine.scanAll();

        assertThat(alertRepository.findAll())
                .extracting(Alert::getType)
                .containsExactlyInAnyOrder(AlertType.ARREARS, AlertType.SESSION_ENDING_SOON);
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

        @Bean
        EndingSoonStubDetector endingSoonStubDetector() {
            return new EndingSoonStubDetector();
        }
    }

    /**
     * فاحص لنوع وتيرته قصيرة، يصف واقعة مسمّاة.
     *
     * <p>{@code SESSION_ENDING_SOON} لا {@code SESSION_STARTING_SOON}: المقصود سلوك
     * المحرّك تجاه الوقائع المسمّاة والوتيرة، لا صحّة فاحص بعينه - وفاحصٌ حقيقيّ هنا
     * يعني تهيئة مجموعة وحصة ومعلم في كل اختبار لأجل ما لا يخصّه.</p>
     */
    static class EndingSoonStubDetector implements AlertDetector {

        /**
         * صامت ما لم يطلب اختبارٌ منه أن ينطق.
         *
         * <p>لأن {@code AlertRuleRegistry} يملأ الأنواع غير المحفوظة بقيمها الافتراضية -
         * وهي مفعَّلة - فأي فاحص ينطق دائماً يُدخل تنبيهه في كل اختبار في الصف، بما فيها
         * تلك التي تتحقّق من أن شيئاً <b>لم</b> يقع.</p>
         */
        private boolean armed;
        private String occurrenceKey = "2026-08-06";

        @Override
        public AlertType type() {
            return AlertType.SESSION_ENDING_SOON;
        }

        @Override
        public List<AlertDraft> detect(AlertRule rule) {
            return armed
                    ? List.of(AlertDraft.occurrence(9L, "مجموعة الثالث الثانوي", occurrenceKey,
                            "مجموعة الثالث الثانوي", "18:00"))
                    : List.of();
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
