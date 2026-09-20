package com.codejava.center.core.security;

import com.codejava.center.core.security.PasswordPolicy.Violation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * القاعدة تقرّر ولا تتكلّم: تعيد ما انكسر، والصياغة المترجَمة في {@code util/Passwords}.
 */
class PasswordPolicyTest {

    @Test
    void acceptsACompatiblePassword() {
        assertThat(PasswordPolicy.check("Strong-Pass-2026")).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(PasswordPolicy.check("short")).contains(Violation.TOO_SHORT);
    }

    /**
     * BCrypt لا يميّز ما بعد 72 بايت. الحرف العربي بايتان في UTF-8، فسبعةٌ وثلاثون حرفاً
     * تتجاوز الحدّ بينما طولها بالمحارف يبدو مقبولاً - وهو الفخّ الذي يجعل كلمتين
     * مختلفتين تفتحان الحساب نفسه.
     */
    @Test
    void rejectsUtf8ValuesBeyondTheBcryptLimit() {
        assertThat(PasswordPolicy.check("\u0633".repeat(37))).contains(Violation.TOO_LONG);
    }

    @Test
    void requiresAnExactConfirmation() {
        assertThat(PasswordPolicy.checkConfirmation("Strong-Pass", "Strong-Pass")).isEmpty();
        assertThat(PasswordPolicy.checkConfirmation("Strong-Pass", "different"))
                .contains(Violation.MISMATCH);
    }
}
