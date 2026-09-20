package com.codejava.center.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * سطح المنصة: من يمرّ إليه، ومن لا يمرّ.
 *
 * <p>ما وراء هذا الحارس ليس بيانات سنتر بل <b>سلطةٌ على السناتر كلها</b>: فتحُ واحد،
 * إيقافُ اشتراك آخر، وقراءةُ من هم. فالخطأ هنا ليس تسرّب صفٍّ بل تسليم المنصة.</p>
 */
class PlatformGuardTest {

    private static final String TOKEN = "s3cret-operator-token";

    @Test
    void aRequestWithoutTheOperatorTokenIsRefused() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        guard(TOKEN).doFilter(platformRequest(null), response, passThrough(reached));

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aRequestWithTheWrongTokenIsRefused() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        guard(TOKEN).doFilter(platformRequest("Bearer not-the-token"), response, passThrough(reached));

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aRequestCarryingTheOperatorTokenPasses() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);

        guard(TOKEN).doFilter(platformRequest("Bearer " + TOKEN),
                new MockHttpServletResponse(), passThrough(reached));

        assertThat(reached).isTrue();
    }

    /**
     * <b>لا رمز مضبوط يعني لا أحد يمرّ.</b>
     *
     * <p>الاتجاه الآخر - "غير مضبوط فليمرّ الجميع" - هو الذي يحوّل متغيّر بيئةٍ منسيّاً
     * في أول نشر إلى منصةٍ يفتح فيها من يطلب سناتر. وهو عطلٌ لا يُبلَّغ عنه لأنه يبدو
     * كأن كل شيء يعمل.</p>
     */
    @Test
    void withNoTokenConfiguredNobodyPasses() throws Exception {
        AtomicBoolean reached = new AtomicBoolean(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        guard(null).doFilter(platformRequest("Bearer " + TOKEN), response, passThrough(reached));

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /** تفعيلُ الدعوة هي الثقب المقصود: صاحب السنتر الجديد لا يملك رمز المشغّل */
    @Test
    void redeemingAnInviteIsTheOneWayInWithoutTheOperatorToken() {
        MockHttpServletRequest redeem = new MockHttpServletRequest();
        redeem.setRequestURI(PlatformOperatorFilter.REDEEM_PATH);

        assertThat(guard(TOKEN).shouldNotFilter(redeem)).isTrue();
    }

    /** وما عداها تحت المسار محروسٌ كلُّه، فنقطةٌ تُضاف غداً تولد محروسة */
    @Test
    void everyOtherPlatformPathIsGuarded() {
        MockHttpServletRequest opening = new MockHttpServletRequest();
        opening.setRequestURI("/api/platform/centres");

        assertThat(guard(TOKEN).shouldNotFilter(opening)).isFalse();
    }

    /** وما ليس تحت المسار لا يخصّ هذا الحارس: شاشات السنتر لها جلستها */
    @Test
    void theCentresOwnApiIsNotThisGuardsBusiness() {
        MockHttpServletRequest students = new MockHttpServletRequest();
        students.setRequestURI("/api/students");

        assertThat(guard(TOKEN).shouldNotFilter(students)).isTrue();
    }

    private PlatformOperatorFilter guard(String token) {
        MockEnvironment environment = new MockEnvironment();
        if (token != null) {
            environment.setProperty(PlatformOperator.TOKEN, token);
        }
        return new PlatformOperatorFilter(new PlatformOperator(environment));
    }

    private MockHttpServletRequest platformRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/platform/centres");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    private FilterChain passThrough(AtomicBoolean reached) {
        return (request, response) -> reached.set(true);
    }
}
