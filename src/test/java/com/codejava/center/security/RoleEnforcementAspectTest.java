package com.codejava.center.security;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * يثبت أن @RequiresRole تُفرض فعلياً عبر AOP.
 * بدون هذا الاختبار قد يبدو الكود مؤمَّناً بينما الـ Aspect لا يعمل إطلاقاً
 * (خطأ شائع: نسيان spring-boot-starter-aop أو استدعاء الدالة من داخل نفس الكلاس).
 */
@SpringBootTest(classes = RoleEnforcementAspectTest.TestConfig.class)
class RoleEnforcementAspectTest {

    @Autowired private UserSession userSession;
    @Autowired private GuardedService guardedService;

    @AfterEach
    void clearSession() {
        userSession.cleanUserSession();
    }

    @Test
    void deniesWhenNoUserIsLoggedIn() {
        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("تسجيل الدخول");
    }

    @Test
    void deniesWhenRoleIsInsufficient() {
        userSession.setCurrentUser(userWithRole(Role.SECRETARY));

        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("مدير نظام");
    }

    @Test
    void allowsWhenRoleMatches() {
        userSession.setCurrentUser(userWithRole(Role.ADMIN));

        assertThat(guardedService.adminOnly()).isEqualTo("ok");
    }

    @Test
    void leavesUnannotatedMethodsOpenToEveryone() {
        userSession.setCurrentUser(userWithRole(Role.SECRETARY));

        // البوابة تستدعي دوالّ غير مقيّدة (خصم رسوم الحصة مثلاً) بصلاحية سكرتارية
        assertThatCode(() -> guardedService.openToAll()).doesNotThrowAnyException();
    }

    private User userWithRole(Role role) {
        return User.builder().id(1L).username("tester").password("x").role(role).build();
    }

    static class GuardedService {
        @RequiresRole(Role.ADMIN)
        public String adminOnly() {
            return "ok";
        }

        public String openToAll() {
            return "ok";
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @Import({UserSession.class, RoleEnforcementAspect.class, GuardedService.class})
    static class TestConfig {
    }
}
