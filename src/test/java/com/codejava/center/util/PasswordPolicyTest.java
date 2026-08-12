package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsACompatiblePassword() {
        assertThatCode(() -> PasswordPolicy.validate("Strong-Pass-2026"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswordsAndUtf8ValuesBeyondBcryptLimit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("س".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAnExactConfirmation() {
        assertThatCode(() -> PasswordPolicy.requireConfirmation("Strong-Pass", "Strong-Pass"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.requireConfirmation("Strong-Pass", "different"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
