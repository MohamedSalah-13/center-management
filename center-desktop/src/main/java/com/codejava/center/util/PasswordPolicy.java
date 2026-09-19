package com.codejava.center.util;

import java.nio.charset.StandardCharsets;

/** سياسة واحدة لكل كلمات المرور التي يخزنها BCrypt في النظام. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    /** يفترض أن الحقل مطلوب وقد تحقق منه المستدعي؛ ويتحقق هنا من حدود الخوارزمية. */
    public static void validate(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(I18n.format(
                    "error.password.tooShort", MIN_LENGTH));
        }
        // BCrypt لا يميّز ما بعد 72 بايت؛ رفض القيمة يمنع كلمتين مختلفتين من التطابق.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException(I18n.get("error.password.tooLong"));
        }
    }

    public static void requireConfirmation(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException(I18n.get("error.password.mismatch"));
        }
    }
}
