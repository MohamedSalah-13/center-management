package com.codejava.center.core.security;

import java.util.Objects;

/**
 * هوية خفيفة لمنفّذ العملية، مستقلة عن كيان JPA وطريقة تسجيل الدخول.
 *
 * <p>{@code userId} قد يكون غائباً لهوية نظامية، أما الاسم والدور فهما القيمتان
 * اللتان تحتاجهما حدود الصلاحيات وسجل المراقبة.</p>
 */
public record ActorIdentity(Long userId, String username, String role) {

    public ActorIdentity {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }

    public boolean hasRole(String expectedRole) {
        return role.equals(Objects.requireNonNull(expectedRole, "expectedRole"));
    }
}
