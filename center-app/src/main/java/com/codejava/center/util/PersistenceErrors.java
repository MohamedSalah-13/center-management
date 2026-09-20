package com.codejava.center.util;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

/** أدوات صغيرة لترجمة تعارضات قاعدة البيانات المتوقعة دون إخفاء الأخطاء الأخرى. */
public final class PersistenceErrors {

    private PersistenceErrors() {
    }

    public static boolean isConstraint(Throwable error, String expectedName) {
        if (error == null || expectedName == null || expectedName.isBlank()) {
            return false;
        }

        String expected = expectedName.toLowerCase(Locale.ROOT);
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().equalsIgnoreCase(expectedName)) {
                return true;
            }

            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
