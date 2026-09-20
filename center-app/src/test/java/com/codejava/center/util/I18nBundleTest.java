package com.codejava.center.util;

import com.codejava.center.core.i18n.LocaleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تحميلُ الحزمة النصية ومصدرُ اللغة خلفها.
 *
 * <p>الأول عطلٌ وقع فعلاً وكان صامتاً: برنامج مضبوط على العربية يعمل كله بالإنجليزية على
 * ويندوز إنجليزي. والثاني هو ما يجعله ممكناً أن يعمل الخادم لاحقاً: قارئان في اللحظة
 * نفسها بلغتين.</p>
 *
 * <p>هنا لا في وحدة الشاشات: موضوعُهما {@code I18n} وهي في طبقة الأعمال، ولأن التحميل
 * البارد لا يُفحص إلا من داخل حزمتها.</p>
 */
class I18nBundleTest {

    private final LocaleProvider providerBefore = I18n.provider();

    @AfterEach
    void restore() {
        I18n.install(providerBefore);
        I18n.clearBundleCache();
    }

    /**
     * العربية حزمة الأساس (بلا لاحقة)، و{@code ResourceBundle} يجرّب لغة الـ JVM
     * الافتراضية قبل الأساس. على ويندوز إنجليزي كان ذلك يجعل طلب العربية يقع على
     * {@code messages_en} فيعمل البرنامج كله بالإنجليزية رغم ضبطه على العربية.
     */
    @Test
    void arabicIsHonouredEvenWhenTheJvmDefaultLocaleIsEnglish() {
        Locale jvmDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            // التحميل بارد وإلا أُعيدت حزمةٌ حُمّلت قبل ضبط الافتراضي، فمرّ الاختبار بلا حارس
            I18n.clearBundleCache();
            I18n.install(() -> I18n.ARABIC);

            assertThat(I18n.get("common.error")).isEqualTo("خطأ");
            assertThat(I18n.isRightToLeft()).isTrue();
        } finally {
            Locale.setDefault(jvmDefault);
        }
    }

    @Test
    void englishBundleIsUsedWhenEnglishIsChosen() {
        I18n.install(() -> I18n.ENGLISH);

        assertThat(I18n.get("common.error")).isEqualTo("Error");
        assertThat(I18n.isRightToLeft()).isFalse();
    }

    /**
     * اللغة تُسأل عند كل نص لا تُلتقط مرة.
     *
     * <p>على سطح المكتب لا فرق: إنسانٌ واحد يبدّل اللغة فتُقرأ الجديدة. وعلى خادمٍ هو
     * الفرق كله - قيمةٌ تُقرأ مرة عند الإقلاع تجعل لغةَ أوّلِ طلبٍ وصل هي لغةَ كل ما
     * بعده، وهو عطلٌ لا يظهر إلا لمن يقرأ صفحةً بلغة غيره.</p>
     */
    @Test
    void theLanguageFollowsWhoeverIsAskingRightNow() {
        AtomicReference<Locale> asker = new AtomicReference<>(I18n.ARABIC);
        I18n.install(asker::get);

        String arabic = I18n.get("common.error");
        asker.set(I18n.ENGLISH);
        String english = I18n.get("common.error");

        assertThat(arabic).isNotEqualTo(english);
    }
}
