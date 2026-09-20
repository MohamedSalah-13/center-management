package com.codejava.center.web;

import com.codejava.center.config.tenancy.ServerTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * يربط الطلب بقاعدة مؤسسته، من جلسته لا من عنوانه.
 *
 * <p>يجري <b>بعد</b> فلاتر المصادقة: المؤسسة تُقرأ من {@link CentreAuthentication}
 * التي كُتبت في الجلسة لحظة الدخول، فلا يملك العميل ما يغيّرها. ولو قُرئت من ترويسة
 * أو من جزءٍ في المسار لكان تبديل نصٍّ في الرابط هو كل ما يلزم ليقرأ موظفُ سنترٍ
 * بيانات سنترٍ آخر.</p>
 *
 * <p>وطلبٌ بلا مصادقة يمرّ بلا مؤسسة - لا بمؤسسةٍ افتراضية. شاشة الدخول وملفات
 * الواجهة لا تسأل القاعدة عن شيء، ومن يسألها بلا نطاق يسقط من
 * {@link ServerTenantContext} برسالةٍ صريحة. وهذا هو المطلوب: خدمةٌ تُستدعى خارج
 * نطاق مؤسسة عطلٌ في البرنامج، وإعطاؤها قاعدةً <i>ما</i> يحوّل العطل إلى بيانات
 * إنسانٍ آخر على شاشة.</p>
 *
 * <p>الدخول نفسه استثناءٌ يدبّره {@code SessionController}: هو يحلّ اسم السنتر عبر
 * {@link CentreDirectory} ثم يعمل داخل نطاقه بنفسه، لأن السؤال هناك يسبق وجود
 * الجلسة التي يقرؤها هذا الفلتر.</p>
 */
public class TenantBindingFilter extends OncePerRequestFilter {

    private final ServerTenantContext tenantContext;

    public TenantBindingFilter(ServerTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof CentreAuthentication centre)) {
            chain.doFilter(request, response);
            return;
        }

        // الاستثناءان المفحوصان يعبران حدّ Runnable ملفوفين ثم يُفكّان: البديل
        // ابتلاعُهما هنا، فيصل إلى الحاوية طلبٌ انتهى بنجاح ظاهريّ ولم يُكتب جوابه.
        try {
            tenantContext.within(centre.tenant(), () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (ServletException e) {
                    throw new WrappedServletException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (WrappedServletException e) {
            throw (ServletException) e.getCause();
        }
    }

    /** لا يوجد {@code UncheckedServletException} في المكتبة القياسية */
    private static final class WrappedServletException extends RuntimeException {

        private WrappedServletException(ServletException cause) {
            super(cause);
        }
    }
}
