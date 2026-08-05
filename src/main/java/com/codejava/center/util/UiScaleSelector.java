package com.codejava.center.util;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/**
 * ضبط قائمة اختيار حجم الخط الموجودة في أكثر من شاشة (الدخول، القائمة الجانبية، الإعدادات).
 *
 * <p>نفس دور {@link LanguageSelector} ونفس سبب وجوده: أربع خطوات متكررة (التعبئة، ومحوّل
 * العرض، وتحديد القيمة الحالية، والمستمع) وثلاث فرص لنسيان الحارس ضد الاستدعاء المتكرر.</p>
 *
 * <p>الفارق عن اللغة أن التبديل هنا <b>لا يعيد بناء الشاشة</b>: حجم الخط سطر تنسيق على
 * جذر المشهد، فيسري على ما هو معروض في نفس اللحظة ولا يضيع ما في الحقول من إدخال.</p>
 */
public final class UiScaleSelector {

    private UiScaleSelector() {
    }

    public static void configure(ComboBox<Double> combo) {
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : UiScale.label(value);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });

        // العلامة التي يتعرّف بها UiScale على القوائم ليحدّثها بعد تغيير من لوحة المفاتيح
        if (!combo.getStyleClass().contains(UiScale.SELECTOR_STYLE_CLASS)) {
            combo.getStyleClass().add(UiScale.SELECTOR_STYLE_CLASS);
        }

        combo.getItems().clear();
        for (double level : UiScale.LEVELS) {
            combo.getItems().add(level);
        }
        combo.setValue(UiScale.factor());

        combo.valueProperty().addListener((observable, oldValue, newValue) -> {
            // الحارس ضروري: UiScale يزامن هذه القائمة بعد كل تغيير، فبدونه يُطلق ذلك
            // الضبط الحدث من جديد ويدور التطبيق على نفسه
            if (newValue == null || newValue == UiScale.factor()) {
                return;
            }
            Scene scene = combo.getScene();
            if (scene == null) {
                UiScale.setFactor(newValue);
                return;
            }
            UiScale.change(scene, newValue);
        });
    }
}
