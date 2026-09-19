package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * الحساب الذي يقرّر مقاس الواجهة، بلا JavaFX ولا سجلّ نظام.
 *
 * <p>نفس سبب وجود {@code PaginationTest} و{@code BackupScheduleTest}: هذه دوال نقية،
 * وخطؤها لا يظهر إلا على شاشة عميل - عاملٌ خارج المدى يعني نافذة أكبر من الشاشة،
 * وفاصلة عشرية بلغة الواجهة تعني سطر تنسيق يُهمَل بصمت فلا يتغيّر شيء.</p>
 */
class UiScaleTest {

    @Test
    void steppingUpStopsAtTheLargestLevel() {
        double largest = UiScale.LEVELS[UiScale.LEVELS.length - 1];

        assertThat(UiScale.next(1.00)).isEqualTo(1.15);
        assertThat(UiScale.next(largest)).isEqualTo(largest);
    }

    @Test
    void steppingDownStopsAtTheSmallestLevel() {
        double smallest = UiScale.LEVELS[0];

        assertThat(UiScale.previous(1.15)).isEqualTo(1.00);
        assertThat(UiScale.previous(smallest)).isEqualTo(smallest);
    }

    @Test
    void steppingCrossesEveryLevelInBothDirections() {
        double value = UiScale.LEVELS[0];
        for (int i = 1; i < UiScale.LEVELS.length; i++) {
            value = UiScale.next(value);
            assertThat(value).isEqualTo(UiScale.LEVELS[i]);
        }
        for (int i = UiScale.LEVELS.length - 2; i >= 0; i--) {
            value = UiScale.previous(value);
            assertThat(value).isEqualTo(UiScale.LEVELS[i]);
        }
    }

    /** قيمة خارج المدى تعني سجلاً عُبث به أو إصداراً أحدث كتب فيه رقماً لا نعرفه */
    @Test
    void clampKeepsTheFactorInsideTheSupportedRange() {
        assertThat(UiScale.clamp(0.1)).isEqualTo(UiScale.LEVELS[0]);
        assertThat(UiScale.clamp(9)).isEqualTo(UiScale.LEVELS[UiScale.LEVELS.length - 1]);
        assertThat(UiScale.clamp(Double.NaN)).isEqualTo(UiScale.DEFAULT_FACTOR);
        assertThat(UiScale.clamp(1.15)).isEqualTo(1.15);
    }

    /**
     * محلل التنسيق في JavaFX يقرأ النقطة العشرية وحدها.
     * لو بُني السطر بلغة الواجهة لكتبت العربية فاصلة، فيُهمَل السطر ويبقى الخط بحجمه
     * الافتراضي: ميزة "لا تعمل إلا بالإنجليزية" لا تُكتشف إلا عند العميل.
     */
    @Test
    void fontSizeStyleUsesADecimalPointWhateverTheUiLanguage() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(I18n.ARABIC);
            assertThat(UiScale.fontSizeStyle())
                    .startsWith("-fx-font-size: ")
                    .contains(".")
                    .doesNotContain(",")
                    .endsWith("px;");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
