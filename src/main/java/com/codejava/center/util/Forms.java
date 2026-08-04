package com.codejava.center.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * قيود الإدخال والتنقّل بين حقول النماذج.
 *
 * <p>الدوال الثلاث منقولة من {@code InputValidator} و{@code FormUtils} في fx-commons،
 * وكانت آخر ما تبقّى من تلك المكتبة بعد أن حلّ {@link Dialogs} محل {@code AlertUtils}.
 * نقلها هنا حذف المكتبة كلها من المشروع: الجرة المُدرجة في {@code lib/}، ومستودع
 * {@code file://} في الـ POM، وحاجة كل جهاز جديد إلى الانتباه لهما.</p>
 *
 * <p>الفاصلة العشرية نقطة لا فاصلة عربية: {@code MoneyUtils} يعرض المبالغ بـ
 * {@code toPlainString}، والعربية هنا مضبوطة على الأرقام اللاتينية
 * ({@code ar-EG-u-nu-latn})، فما يراه المستخدم في الجدول هو ما يُفترض أن يكتبه.</p>
 */
public final class Forms {

    /** ما يُسمح بإدخاله في حقل رقمي صحيح */
    private static final String DIGITS = "[0-9]*";

    /** رقم عشري كامل: خانات، ونقطة واحدة على الأكثر، وخانات بعدها */
    private static final String DECIMAL = "([0-9]*)?(\\.[0-9]*)?";

    private Forms() {
    }

    /**
     * يقصر الحقل على الأرقام الصحيحة.
     * الفحص على النص المُدخَل وحده ({@code getText})، فالحذف يمرّ دائماً لأن نصّه فارغ.
     */
    public static void numericOnly(TextField... fields) {
        for (TextField field : fields) {
            field.setTextFormatter(new TextFormatter<>(change ->
                    change.getText().matches(DIGITS) ? change : null));
        }
    }

    /**
     * يقصر الحقل على رقم عشري.
     *
     * <p>الفحص هنا على النص <b>الناتج</b> ({@code getControlNewText}) لا على المُدخَل، بخلاف
     * {@link #numericOnly}: النقطة وحدها ليست مقبولة أو مرفوضة بذاتها - تُقبل في "12" فتصير
     * "12."، وتُرفض في "12.5" لأنها ستُنتج نقطتين. لا يُعرف ذلك إلا بالنظر إلى القيمة كاملة.</p>
     */
    public static void decimalOnly(TextField... fields) {
        for (TextField field : fields) {
            field.setTextFormatter(new TextFormatter<>(change ->
                    change.getControlNewText().matches(DECIMAL) ? change : null));
        }
    }

    /**
     * زر Enter ينقل التركيز إلى الحقل التالي بترتيب الوسائط، والأخير لا ينقل شيئاً.
     *
     * <p>يُضاف كمستمع ({@code addEventHandler}) لا بـ {@code setOnKeyPressed}: الثاني يمحو
     * أي معالج آخر على نفس الحقل بصمت، وشاشة الحضور مثلاً تعتمد على Enter لتسجيل الباركود.</p>
     */
    public static void focusNextOnEnter(TextField... fields) {
        for (int i = 0; i < fields.length - 1; i++) {
            TextField next = fields[i + 1];
            fields[i].addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    next.requestFocus();
                }
            });
        }
    }
}
