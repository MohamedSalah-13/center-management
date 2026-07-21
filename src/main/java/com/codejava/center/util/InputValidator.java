package com.codejava.center.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import java.util.function.UnaryOperator;

public class InputValidator {

    /**
     * دالة مخصصة للأرقام الصحيحة فقط (مثل أرقام التليفونات، الباركود، السعة)
     * تمنع كتابة أي حروف أو رموز أو مسافات.
     */
    public static void makeNumericOnly(TextField... textFields) {
        for (TextField textField : textFields) {
            UnaryOperator<TextFormatter.Change> filter = change -> {
                String text = change.getText();
                // السماح بالأرقام فقط (من 0 إلى 9)
                if (text.matches("[0-9]*")) {
                    return change;
                }
                return null; // رفض التغيير إذا احتوى على حروف
            };
            textField.setTextFormatter(new TextFormatter<>(filter));
        }
    }

    /**
     * دالة مخصصة للأرقام العشرية (مثل الأسعار والمبالغ المالية)
     * تسمح بالأرقام مع نقطة عشرية واحدة فقط.
     */
    public static void makeDecimalOnly(TextField... textFields) {
        for (TextField textField : textFields) {
            UnaryOperator<TextFormatter.Change> filter = change -> {
                String newText = change.getControlNewText();
                // السماح بالأرقام أو الأرقام مع نقطة عشرية
                if (newText.matches("([0-9]*)?(\\.[0-9]*)?")) {
                    return change;
                }
                return null; // رفض التغيير
            };
            textField.setTextFormatter(new TextFormatter<>(filter));
        }
    }
}