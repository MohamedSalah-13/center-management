package com.codejava.center.repository;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Alert;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * عدّادُ التنبيهات الحرجة القائمة، وهو ما يقرؤه مسحُ المنصة عن كل سنتر.
 *
 * <p>اسمٌ مشتَق ({@code countBySeverityAndAcknowledgedAtIsNull}) يُحلّ وقت التشغيل لا
 * وقت الترجمة: خطأٌ في اسم خاصيّة لا يوقف البناء بل يُسقط السياق عند أول إقلاع
 * حقيقي - وهذا هو موضعُ الفحص، كما في بقية هذا المجلد.</p>
 *
 * <p>والفرزُ بالدرجة هو المقصود لا مجرّد العدّ: {@code PAYMENT_RECEIPT} درجتُه
 * {@code INFO} ويُطلق عشراتِ المرات في اليوم، فعدٌّ يشملها يقرأ رقماً كبيراً في كل
 * سنترٍ يعمل - أي رقماً لا يميّز المزدحم من المتعطّل.</p>
 */
@DataJpaTest
@Import(SecurityConfig.class)
class OpenCriticalCountTest {

    @Autowired private AlertRepository alertRepository;

    @Test
    void countsOnlyCriticalAlertsThatNobodyHasActedOnYet() {
        raise(AlertType.BACKUP_FAILED, AlertSeverity.CRITICAL, null);
        raise(AlertType.BACKUP_OVERDUE, AlertSeverity.CRITICAL, null);

        // عولج: بقي في الجدول - العلامة لا تحذف - ولا يُعدّ قائماً
        raise(AlertType.SESSION_LEFT_OPEN, AlertSeverity.CRITICAL, LocalDateTime.now());

        // وإيصالُ الدفع قائم، ودرجتُه ليست حرجة: هو الضجيج الذي يوجد الفرزُ لأجله
        raise(AlertType.PAYMENT_RECEIPT, AlertSeverity.INFO, null);
        raise(AlertType.ABSENCE, AlertSeverity.WARNING, null);

        assertThat(alertRepository
                .countBySeverityAndAcknowledgedAtIsNull(AlertSeverity.CRITICAL))
                .isEqualTo(2);

        assertThat(alertRepository.countByAcknowledgedAtIsNull())
                .as("والمجموعُ يعدّ الضجيج معها، وهو ما لا يصلح للمسح")
                .isEqualTo(4);
    }

    private void raise(AlertType type, AlertSeverity severity, LocalDateTime acknowledgedAt) {
        alertRepository.saveAndFlush(Alert.builder()
                .type(type)
                .severity(severity)
                .raisedAt(LocalDateTime.now())
                .dedupeKey(UUID.randomUUID().toString())
                .acknowledgedAt(acknowledgedAt)
                .build());
    }
}
