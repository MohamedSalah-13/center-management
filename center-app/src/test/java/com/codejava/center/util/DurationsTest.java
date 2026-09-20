package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صياغة مدة المكوث. ثلاثة مواضع تعرضها - الشاشتان والورقة - فاختلافها بينها يجعل
 * الموظف يقرأ رقماً على الشاشة وآخر على الورقة عن الطالب نفسه.
 *
 * <p>المقارنة بالمفاتيح لا بالنصّ: اللغة تُحفظ لكل جهاز، واختبارٌ يقارن بالعربية
 * يسقط أول ما يبدّل أحدهم لغة البرنامج.</p>
 */
class DurationsTest {

    @Test
    void showsHoursOnlyWhenThereAreAny() {
        assertThat(Durations.format(Duration.ofMinutes(45)))
                .isEqualTo(I18n.format("common.duration.minutes", 45L));

        assertThat(Durations.format(Duration.ofMinutes(80)))
                .isEqualTo(I18n.format("common.duration.hoursMinutes", 1L, 20L));
    }

    /** الساعة التامة تُذكر بصفر دقائقها: "1 س" وحدها تبدو نصّاً مبتوراً */
    @Test
    void aWholeHourKeepsItsZeroMinutes() {
        assertThat(Durations.format(Duration.ofHours(2)))
                .isEqualTo(I18n.format("common.duration.hoursMinutes", 2L, 0L));
    }

    /** المدة المجهولة - من أُغلقت حصته بلا تسجيل انصراف - لا تُعرض صفراً */
    @Test
    void anUnknownDurationIsNotZero() {
        assertThat(Durations.format(null)).isEqualTo(I18n.get("common.none"));
    }

    /**
     * المدة السالبة تعني ساعةً مضبوطة خطأً على أحد الأجهزة، أو وقتاً أُدخل يدوياً.
     * عرضها كما هي يُقرأ على أن الطالب انصرف قبل أن يدخل.
     */
    @Test
    void aNegativeDurationIsTreatedAsUnknown() {
        assertThat(Durations.format(Duration.ofMinutes(-10))).isEqualTo(I18n.get("common.none"));
    }
}
