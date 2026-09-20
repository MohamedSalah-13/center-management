package com.codejava.center.security;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AuditService;
import com.codejava.center.util.I18n;
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
 * صاحب السنتر. {@link AuditService#recordAccessDenied} تكتب في معاملة مستقلة لأن
 * الاستثناء الذي يلي هذا السطر يُلغي المعاملة الجارية - وهي بابٌ ضيّق بنوع حدثٍ مثبَّت:
 * من يستطيع تسمية الحدث يستطيع كتابة سطرٍ عن فشلٍ لم يقع، وسجلٌّ كهذا ليس شهادة.</p>
 *
 * <p>لا يجوز أن تحمل أيّ من دوالّ {@code AuditService} حارس {@code @RequiresRole}:
 * الحارس يستدعيها عند الرفض، فتصير محاولة تسجيل الرفض رفضاً جديداً بلا نهاية.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RoleEnforcementAspect {

    private final CurrentActor currentActor;
    private final AuditService auditService;

    @Before("@annotation(requiresRole)")
    public void enforce(JoinPoint joinPoint, RequiresRole requiresRole) {
        ActorIdentity actor = currentActor.currentActor();

        if (actor == null) {
            auditService.recordAccessDenied(operationOf(joinPoint), "reason=no-session");
            throw new AccessDeniedException(I18n.get("error.access.noSession"));
        }

        Role[] allowed = requiresRole.value();
        boolean permitted = Arrays.stream(allowed).anyMatch(role -> actor.hasRole(role.name()));

        if (!permitted) {
            // بصيغة محايدة لغوياً: السطر يُقرأ لاحقاً بأي لغة كانت الواجهة عليها وقتها
            auditService.recordAccessDenied(operationOf(joinPoint),
                    "required=" + Arrays.stream(allowed).map(Enum::name).collect(Collectors.joining("|"))
                            + "; actual=" + actor.role());

            String allowedNames = Arrays.stream(allowed)
                    .map(Role::getDisplayName)
                    .collect(Collectors.joining(" " + I18n.get("common.or") + " "));
            throw new AccessDeniedException(I18n.format("error.access.denied",
                    allowedNames, displayRole(actor.role())));
        }
    }

    private String displayRole(String role) {
        try {
            return Role.valueOf(role).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            return role;
        }
    }

    /** اسم العملية المرفوضة كما هو في الكود، مثل {@code TransactionService.recordExpense} */
    private String operationOf(JoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }
}
