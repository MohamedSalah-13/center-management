package com.codejava.center.security;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * يفرض {@link RequiresRole} على طبقة الخدمات.
 * يقرأ المستخدم من جلسة التطبيق وليس من ThreadLocal، فيعمل بشكل صحيح
 * حتى عندما تُستدعى الخدمة من خيط خلفي عبر CompletableFuture.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RoleEnforcementAspect {

    private final UserSession userSession;

    @Before("@annotation(requiresRole)")
    public void enforce(RequiresRole requiresRole) {
        User currentUser = userSession.getCurrentUser();

        if (currentUser == null) {
            throw new AccessDeniedException(I18n.get("error.access.noSession"));
        }

        Role[] allowed = requiresRole.value();
        boolean permitted = Arrays.asList(allowed).contains(currentUser.getRole());

        if (!permitted) {
            String allowedNames = Arrays.stream(allowed)
                    .map(Role::getDisplayName)
                    .collect(Collectors.joining(" " + I18n.get("common.or") + " "));
            throw new AccessDeniedException(I18n.format("error.access.denied",
                    allowedNames, currentUser.getRole().getDisplayName()));
        }
    }
}
