package com.codejava.center.security;

import com.codejava.center.domain.enums.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * تقييد استدعاء دالة الخدمة على صلاحيات محددة.
 * يفرضها {@link RoleEnforcementAspect} على مستوى طبقة الخدمات،
 * فلا يكفي إخفاء الزر في الواجهة لتجاوزها.
 *
 * <p>لم نستخدم @PreAuthorize من Spring Security لأن كل استدعاءات الخدمات هنا
 * تجري على خيوط ForkJoinPool عبر CompletableFuture، بينما SecurityContextHolder
 * يعتمد ThreadLocal افتراضياً فلا ينتقل سياق المستخدم إلى تلك الخيوط.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    Role[] value();
}
