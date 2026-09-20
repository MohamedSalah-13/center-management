package com.codejava.center.web;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ربط الطلب بقاعدة مؤسسته.
 *
 * <p>ثلاثة أشياء تفشل هنا بصمت، ولذلك ثلاثة اختبارات: أن يعمل المتحكّم <b>خارج</b>
 * نطاق مؤسسة فيسقط - أو أسوأ، أن يعمل داخل نطاق مؤسسةٍ أخرى بقي من طلبٍ سابق على
 * الخيط نفسه؛ وأن يُبتلع استثناءٌ مفحوص عند حدّ {@code Runnable} فتنتهي الحاوية إلى
 * طلبٍ "ناجح" بلا جواب مكتوب.</p>
 *
 * <p>{@code null} مكان السجلّ مقصود: هذا الاختبار يقيس {@code within} وهي لا تسأل
 * السجلّ عن شيء - من يسأله هو {@code sweep}، وله اختباره.</p>
 */
class TenantBindingFilterTest {

    private static final TenantId CAIRO = new TenantId(7L);

    private final ServerTenantContext tenantContext = new ServerTenantContext(null);

    private final TenantBindingFilter filter = new TenantBindingFilter(tenantContext);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theChainRunsInsideTheCentreCarriedByTheSession() throws Exception {
        signedInTo(CAIRO);
        AtomicReference<TenantId> seen = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> seen.set(tenantContext.currentTenant()));

        assertThat(seen.get()).isEqualTo(CAIRO);
    }

    /**
     * ويُفكّ الربط بعد الطلب: خيوط الحاوية يُعاد استعمالها، ومؤسسةٌ تبقى على الخيط
     * تعني أن الطلب التالي - وقد يكون لسنترٍ آخر أو لا سنتر له - يقرأ قاعدةَ من سبقه.
     */
    @Test
    void theThreadCarriesNoCentreOnceTheRequestIsDone() throws Exception {
        signedInTo(CAIRO);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                });

        assertThatThrownBy(tenantContext::currentTenant)
                .isInstanceOf(IllegalStateException.class);
    }

    /** طلبٌ بلا جلسة يمرّ بلا مؤسسة - لا بمؤسسةٍ افتراضية */
    @Test
    void aRequestWithoutASessionIsNotGivenSomeCentre() throws Exception {
        AtomicReference<Boolean> bound = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> bound.set(tenantContext.isBound()));

        assertThat(bound.get()).isFalse();
    }

    /**
     * مصادقةٌ ليست مصادقةَ سنتر لا تربط شيئاً.
     *
     * <p>{@code CentreAuthentication} وحدها تحمل المؤسسة، ورمزٌ آخر في السياق -
     * مجهولٌ، أو رمزٌ يضعه اختبار - ليس فيه ما يُربط به.</p>
     */
    @Test
    void anAuthenticationThatIsNotACentresBindsNothing() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));
        AtomicReference<Boolean> bound = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> bound.set(tenantContext.isBound()));

        assertThat(bound.get()).isFalse();
    }

    @Test
    void aFailureInsideTheChainReachesTheContainerRatherThanBeingSwallowed() {
        signedInTo(CAIRO);
        IOException thrown = new IOException("القرص امتلأ");

        Throwable caught = catchThrowable(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(),
                failingWith(thrown)));

        assertThat(caught).isSameAs(thrown);
    }

    @Test
    void aServletFailureInsideTheChainReachesTheContainerToo() {
        signedInTo(CAIRO);
        ServletException thrown = new ServletException("المتحكّم سقط");

        Throwable caught = catchThrowable(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(),
                failingWith(thrown)));

        assertThat(caught).isSameAs(thrown);
    }

    private FilterChain failingWith(Exception failure) {
        return (request, response) -> {
            if (failure instanceof IOException io) {
                throw io;
            }
            throw (ServletException) failure;
        };
    }

    private void signedInTo(TenantId tenant) {
        SecurityContextHolder.getContext().setAuthentication(
                CentreAuthentication.of(1L, "admin", Role.ADMIN, tenant));
    }
}
