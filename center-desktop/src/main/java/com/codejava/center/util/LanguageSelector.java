package com.codejava.center.util;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * ضبط قائمة اختيار اللغة الموجودة في أكثر من شاشة (الدخول، القائمة الجانبية، الإعدادات).
 *
 * <p>كل موضع يحتاج نفس أربع الخطوات: تعبئة اللغات، ومحوّل عرض يُظهر كل لغة باسمها هي،
 * وتحديد اللغة الحالية، ومستمع يبدّل ويعيد بناء الشاشة. تكرارها ثلاث مرات كان يعني
 * ثلاث فرص لنسيان الحارس ضد الاستدعاء المتكرر أدناه.</p>
 */
public final class LanguageSelector {

    private LanguageSelector() {
    }

    /**
     * @param combo    القائمة المعرَّفة في FXML
     * @param onChange ما يُنفَّذ بعد تبديل اللغة - عادةً إعادة بناء المشهد الحالي
     */
    public static void configure(ComboBox<Locale> combo, Runnable onChange) {
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale locale) {
                return locale == null ? "" : I18n.displayName(locale);
            }

            @Override
            public Locale fromString(String string) {
                return null;
            }
        });

        combo.getItems().setAll(I18n.supportedLocales());
        combo.setValue(I18n.current());

        combo.valueProperty().addListener((observable, oldLocale, newLocale) -> {
            // الحارس ضروري: إعادة بناء المشهد تُنشئ قائمة جديدة وتضبط قيمتها على اللغة
            // الحالية، فبدونه يُطلق ذلك الضبط الحدث من جديد وتدور إعادة البناء بلا نهاية
            if (newLocale == null || newLocale.equals(I18n.current())) {
                return;
            }
            I18n.setLocale(newLocale);
            onChange.run();
        });
    }
}
