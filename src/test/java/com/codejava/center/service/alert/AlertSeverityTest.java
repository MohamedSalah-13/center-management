package com.codejava.center.service.alert;

import com.codejava.center.domain.enums.AlertSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ترتيب الدرجات، وهو مقلوب عمّا يتوقعه القارئ.
 *
 * <p>{@code CRITICAL} هو <b>الأصغر</b> ترتيباً لأنه الأعلى إلحاحاً، فمقارنةٌ تُكتب
 * يوماً بـ {@code >=} بدل {@code <=} تقلب المعنى تماماً: يصمت البرنامج عن الحرج ويقفز
 * بالمعلومات. ولا شيء يفشل حينها - لا بناء ولا اختبار آخر - لأن الإشعارات تظهر
 * وتعمل، وكل ما في الأمر أنها الإشعارات الخطأ.</p>
 */
class AlertSeverityTest {

    @Test
    void severitiesAreDeclaredFromMostToLeastUrgent() {
        assertThat(AlertSeverity.CRITICAL.ordinal())
                .isLessThan(AlertSeverity.WARNING.ordinal());
        assertThat(AlertSeverity.WARNING.ordinal())
                .isLessThan(AlertSeverity.INFO.ordinal());
    }

    @Test
    void aSeverityAlwaysMeetsItsOwnThreshold() {
        for (AlertSeverity severity : AlertSeverity.values()) {
            assertThat(severity.isAtLeast(severity)).isTrue();
        }
    }

    /** حدٌّ عند "تحذير" يمرّر الحرج ويحجب المعلومة - وهذا هو الافتراضي في الجهاز */
    @Test
    void aThresholdPassesEverythingMoreUrgentAndBlocksTheRest() {
        assertThat(AlertSeverity.CRITICAL.isAtLeast(AlertSeverity.WARNING)).isTrue();
        assertThat(AlertSeverity.INFO.isAtLeast(AlertSeverity.WARNING)).isFalse();
    }

    /** أدنى حدّ يمرّر كل شيء، فلا يوجد ضبط يُسقط تنبيهاً حرجاً بصمت */
    @Test
    void theLowestThresholdPassesEverySeverity() {
        for (AlertSeverity severity : AlertSeverity.values()) {
            assertThat(severity.isAtLeast(AlertSeverity.INFO)).isTrue();
        }
    }
}
