package com.codejava.center.core.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * سياسة واحدة لكل كلمات المرور التي يخزنها BCrypt في النظام.
 *
 * <p>تقرّر ولا تتكلّم: تعيد ما انكسر ({@link Violation}) ولا تبني رسالة. الرسالة مترجَمة
 * بلغة الجهاز، والنواة لا تعرف حزمة نصوص — والقاعدة نفسها لا تتغيّر بلغة من يقرؤها.
 * راجع {@code util/Passwords} على جهة التطبيق.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BCRYPT_BYTES = 72;

    /** ما يمكن أن ينكسر في كلمة مرور */
    public enum Violation {
        TOO_SHORT,
        /** BCrypt لا يميّز ما بعد 72 بايت، فكلمتان مختلفتان تتطابقان عنده */
        TOO_LONG,
        MISMATCH
    }

    private PasswordPolicy() {
    }

    /**
     * يفترض أن الحقل مطلوب وقد تحقق منه المستدعي؛ ويتحقق هنا من حدود الخوارزمية.
     *
     * @return ما انكسر، أو فارغ إن كانت مقبولة
     */
    public static Optional<Violation> check(String password) {
        if (password.length() < MIN_LENGTH) {
            return Optional.of(Violation.TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            return Optional.of(Violation.TOO_LONG);
        }
        return Optional.empty();
    }

    public static Optional<Violation> checkConfirmation(String password, String confirmation) {
        return password.equals(confirmation) ? Optional.empty() : Optional.of(Violation.MISMATCH);
    }
}
