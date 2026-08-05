package com.codejava.center.security;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AuditService;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * يثبت أن @RequiresRole تُفرض فعلياً عبر AOP.
 * بدون هذا الاختبار قد يبدو الكود مؤمَّناً بينما الـ Aspect لا يعمل إطلاقاً
 * (خطأ شائع: نسيان spring-boot-starter-aop أو استدعاء الدالة من داخل نفس الكلاس).
 *
 * <p>ويثبت أيضاً أن كل رفض يصل إلى سجل المراقبة: الرفض الذي لا يُسجَّل يمنع محاولة
 * واحدة ولا يُظهر تكرارها، وهو ما يحوّل السجل من دليل إلى ديكور.</p>
 */
@SpringBootTest(classes = RoleEnforcementAspectTest.TestConfig.class)
class RoleEnforcementAspectTest {

    @Autowired private UserSession userSession;
    @Autowired private GuardedService guardedService;
    @Autowired private AuditService auditService;

    @AfterEach
    void clearSession() {
        userSession.cleanUserSession();
        reset(auditService); // الـ mock مشترك بين الاختبارات لأن السياق يُعاد استعماله
    }

    // النصوص تُقارَن بمفاتيح الحزمة لا بالعربية الحرفية: لغة الواجهة تُحفظ لكل جهاز،
    // فتبديلها في التطبيق كان يُفشل هذه الاختبارات على جهاز المطوّر وحده.

    @Test
    void deniesWhenNoUserIsLoggedIn() {
        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(I18n.get("error.access.noSession"));
    }

    @Test
    void deniesWhenRoleIsInsufficient() {
        userSession.setCurrentUser(userWithRole(Role.SECRETARY));

        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Role.ADMIN.getDisplayName())
                .hasMessageContaining(Role.SECRETARY.getDisplayName());
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

    /**
     * السطر المسجَّل يحمل اسم العملية المرفوضة والصلاحيتين معاً: "رُفض وصول" وحده
     * لا يفرّق بين خطأ في التنقّل ومحاولة متكررة على دوالّ الخزينة.
     */
    @Test
    void recordsTheDenialWithTheOperationAndBothRoles() {
        userSession.setCurrentUser(userWithRole(Role.SECRETARY));

        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class);

        ArgumentCaptor<String> operation = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFailure(eq(AuditAction.ACCESS_DENIED),
                operation.capture(), details.capture());

        assertThat(operation.getValue()).isEqualTo("GuardedService.adminOnly");
        // بأسماء الـ enum لا بأسمائها المعروضة: السطر يُقرأ لاحقاً بأي لغة
        assertThat(details.getValue()).contains(Role.ADMIN.name()).contains(Role.SECRETARY.name());
    }

    @Test
    void recordsTheDenialWhenThereIsNoSessionAtAll() {
        assertThatThrownBy(() -> guardedService.adminOnly())
                .isInstanceOf(AccessDeniedException.class);

        verify(auditService).recordFailure(AuditAction.ACCESS_DENIED,
                "GuardedService.adminOnly", "reason=no-session");
    }

    @Test
    void recordsNothingWhenAccessIsGranted() {
        userSession.setCurrentUser(userWithRole(Role.ADMIN));

        guardedService.adminOnly();

        verify(auditService, never()).recordFailure(any(), any(), any());
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

        /**
         * بديل عن {@code AuditService} الحقيقي: هو يحتاج قاعدة بيانات، والمقصود هنا
         * إثبات أن الحارس يستدعيه لا أن الكتابة تنجح - وتلك يغطّيها اختبار المستودع.
         */
        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }
    }
}
