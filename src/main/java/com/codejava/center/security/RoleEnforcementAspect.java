package com.codejava.center.security;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AuditService;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * يفرض {@link RequiresRole} على طبقة الخدمات.
 * يقرأ المستخدم من جلسة التطبيق وليس من ThreadLocal، فيعمل بشكل صحيح
 * حتى عندما تُستدعى الخدمة من خيط خلفي عبر CompletableFuture.
 *
 * <p>كل رفض يُكتب في سجل المراقبة. المنع وحده لا يكفي: محاولة سكرتارية فتح شاشة
 * ليست لها تُفسَّر بخطأ في التنقّل، أما تكرارها على دوالّ الخزينة فهو ما يجب أن يراه
 * صاحب السنتر. {@link AuditService#recordFailure} تكتب في معاملة مستقلة لأن الاستثناء
 * الذي يلي هذا السطر يُلغي المعاملة الجارية.</p>
 *
 * <p>لا يجوز أن تحمل أيّ من دوالّ {@code AuditService} حارس {@code @RequiresRole}:
 * الحارس يستدعيها عند الرفض، فتصير محاولة تسجيل الرفض رفضاً جديداً بلا نهاية.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RoleEnforcementAspect {

    private final UserSession userSession;
    private final AuditService auditService;

    @Before("@annotation(requiresRole)")
    public void enforce(JoinPoint joinPoint, RequiresRole requiresRole) {
        User currentUser = userSession.getCurrentUser();

        if (currentUser == null) {
            auditService.recordFailure(AuditAction.ACCESS_DENIED,
                    operationOf(joinPoint), "reason=no-session");
            throw new AccessDeniedException(I18n.get("error.access.noSession"));
        }

        Role[] allowed = requiresRole.value();
        boolean permitted = Arrays.asList(allowed).contains(currentUser.getRole());

        if (!permitted) {
            // بصيغة محايدة لغوياً: السطر يُقرأ لاحقاً بأي لغة كانت الواجهة عليها وقتها
            auditService.recordFailure(AuditAction.ACCESS_DENIED, operationOf(joinPoint),
                    "required=" + Arrays.stream(allowed).map(Enum::name).collect(Collectors.joining("|"))
                            + "; actual=" + currentUser.getRole().name());

            String allowedNames = Arrays.stream(allowed)
                    .map(Role::getDisplayName)
                    .collect(Collectors.joining(" " + I18n.get("common.or") + " "));
            throw new AccessDeniedException(I18n.format("error.access.denied",
                    allowedNames, currentUser.getRole().getDisplayName()));
        }
    }

    /** اسم العملية المرفوضة كما هو في الكود، مثل {@code TransactionService.recordExpense} */
    private String operationOf(JoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }
}
