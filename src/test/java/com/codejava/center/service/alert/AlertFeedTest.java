package com.codejava.center.service.alert;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Alert;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.AlertRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ما يصل الشاشة من التنبيهات، ومتى.
 *
 * <p>ثلاثة أعطال يغطّيها هذا الصف، ولا يظهر أيٌّ منها في اختبار آخر لأن كلّها في
 * <b>ما لا يحدث</b> لا فيما يحدث:</p>
 *
 * <ul>
 *   <li>تنبيه يُسلَّم مرتين فتقفز بطاقتان متطابقتان - وهو ما يقع لحظة الفحص اليدوي
 *       حين تتسابق النبضة الدورية مع حدث التنبيه الجديد.</li>
 *   <li>ما تراكم قبل فتح الشاشة ينهال دفعةً واحدة عند كل تسجيل دخول.</li>
 *   <li>تنبيه عالجه زميلٌ على جهازه يظلّ يقفز على هذا الجهاز.</li>
 * </ul>
 *
 * <p>القفزة إلى خيط الواجهة تُستبدل بتنفيذ مباشر: أدوات JavaFX ليست مُقلعة في خادم
 * البناء، و{@code Platform.runLater} ترمي هناك.</p>
 */
@DataJpaTest
@Import({AlertFeed.class, SecurityConfig.class, AlertFeedTest.SchedulerConfig.class})
class AlertFeedTest {

    @Autowired private AlertFeed alertFeed;
    @Autowired private AlertRepository alertRepository;

    private final List<AlertBatch> delivered = new ArrayList<>();

    @BeforeEach
    void attachSink() {
        delivered.clear();
        alertFeed.dispatchOn(Runnable::run);
        alertFeed.attach(delivered::add);
    }

    @AfterEach
    void detach() {
        alertFeed.detach();
    }

    /**
     * خط البداية عند فتح الشاشة. بدونه يستقبل المدير عند كل تسجيل دخول كل ما تراكم منذ
     * أسبوع بطاقةً بطاقة - وهي تنبيهات رآها بالأمس.
     */
    @Test
    void alertsRaisedBeforeTheScreenOpenedAreNotAnnounced() {
        persistAlert("قبل الفتح");
        alertFeed.attach(delivered::add); // إعادة التسجيل كما يحدث عند فتح لوحة القيادة

        alertFeed.poll();

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).fresh()).isEmpty();
    }

    @Test
    void anAlertRaisedAfterwardsIsDeliveredOnce() {
        persistAlert("تنبيه جديد");

        alertFeed.poll();
        alertFeed.poll();

        List<Alert> announced = delivered.stream().flatMap(batch -> batch.fresh().stream()).toList();
        assertThat(announced).extracting(Alert::getEntityLabel).containsExactly("تنبيه جديد");
    }

    /** تنبيه عالجه زميل على جهازه لا يقفز هنا: القراءة تستثني المعالَج */
    @Test
    void acknowledgedAlertsAreNeverAnnounced() {
        Alert handled = persistAlert("عولج على جهاز آخر");
        handled.setAcknowledgedAt(LocalDateTime.now());
        handled.setAcknowledgedBy("مدير");
        alertRepository.saveAndFlush(handled);

        alertFeed.poll();

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).fresh()).isEmpty();
        assertThat(delivered.get(0).openCount()).isZero();
    }

    /**
     * التسليم يقع ولو لم يستجدّ شيء: العدّاد يتغيّر أيضاً حين يعالج زميلٌ تنبيهاً على
     * جهازه، ورقمٌ عالق على قيمة الأمس أسوأ من رقم متأخر بدقيقتين.
     */
    @Test
    void theOpenCountIsDeliveredEvenWhenNothingIsNew() {
        persistAlert("قائم");
        alertFeed.attach(delivered::add);

        alertFeed.poll();

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).hasFresh()).isFalse();
        assertThat(delivered.get(0).openCount()).isEqualTo(1);
    }

    /** لا شيء يُسلَّم بعد انسحاب الشاشة - تسجيل خروج، أو تبديل لغة */
    @Test
    void nothingIsDeliveredAfterDetaching() {
        alertFeed.detach();
        persistAlert("بعد الانسحاب");

        alertFeed.poll();

        assertThat(delivered).isEmpty();
    }

    private Alert persistAlert(String label) {
        return alertRepository.saveAndFlush(Alert.builder()
                .type(AlertType.SESSION_LEFT_OPEN)
                .severity(AlertSeverity.CRITICAL)
                .raisedAt(LocalDateTime.now())
                .entityId(7L)
                .entityLabel(label)
                .args(label + Alert.ARG_SEPARATOR + "2026-08-01")
                .dedupeKey("TEST:" + label)
                .build());
    }

    /**
     * {@code @DataJpaTest} شريحة بيانات ولا تحمل مجدوِلاً، و{@link AlertFeed} يحقنه
     * ليملك نبضته بنفسه. الاختبار ينادي {@code poll} مباشرةً فلا يعتمد على توقيته.
     */
    @TestConfiguration
    static class SchedulerConfig {
        @Bean
        TaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.initialize();
            return scheduler;
        }
    }
}
