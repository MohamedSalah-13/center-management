package com.codejava.center.web;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * خادم المنصة: السنتر يُعرف باسمه في سجلّ المنصة، والطلب يعمل داخل قاعدته.
 */
@Configuration
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled", havingValue = "true")
public class PlatformCentreConfig {

    /**
     * اسم السنتر إلى معرّفه.
     *
     * <p>ورسالةُ الرفض واحدة لاسمٍ مجهول ولاشتراكٍ متوقف: التفريق بينهما يقول لمن
     * يجرّب الأسماء أيُّها سنترٌ قائم - وهو نصف ما يحتاجه قبل أن يجرّب كلمات المرور.</p>
     */
    @Bean
    public CentreDirectory platformCentreDirectory(TenantRegistry registry) {
        return slug -> {
            PlatformTenant tenant = registry.findBySlug(slug == null ? "" : slug.trim())
                    .filter(candidate -> candidate.status().isServed())
                    .orElseThrow(() -> new IllegalArgumentException("unknown centre"));
            return tenant.id();
        };
    }

    /**
     * المؤسسة المربوطة بهذا الخيط، إن رُبطت.
     *
     * <p>{@code isBound} لا {@code currentTenant} داخل {@code try}: السؤال "هل ثمّة
     * مؤسسة" سؤالٌ مشروع، والإجابة عنه باستثناءٍ يُلتقط تجعل من حالةٍ عادية - طلبٌ
     * قبل الدخول - سطرَ خطأ في كل سجلّ.</p>
     */
    @Bean
    public CurrentCentre platformCurrentCentre(ServerTenantContext tenantContext) {
        return () -> tenantContext.isBound() ? tenantContext.currentTenant() : null;
    }

    /**
     * حارسُ سطح المنصة.
     *
     * <p>هنا لا في {@code WebSecurityConfig}: بلا تعدّد مؤسسات لا سطح منصة أصلاً - ولا
     * {@code TenantProvisioning} يُستدعى - فالشرط على التهيئة أصدق من شرطٍ داخل
     * الفلتر.</p>
     */
    @Bean
    public PlatformOperatorFilter platformOperatorFilter(PlatformOperator operator) {
        return new PlatformOperatorFilter(operator);
    }

    /**
     * يربط كل طلب بقاعدة مؤسسته.
     *
     * <p>يُسجَّل هنا لا في {@code WebSecurityConfig}: بلا {@link ServerTenantContext}
     * لا معنى للربط أصلاً، والشرط على التهيئة أصدق من شرط داخل الفلتر.</p>
     */
    @Bean
    public TenantBindingFilter tenantBindingFilter(ServerTenantContext tenantContext) {
        return new TenantBindingFilter(tenantContext);
    }
}
