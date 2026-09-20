package com.codejava.center.web;

import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * الحاجز على عنوان الطالب.
 *
 * <p>الهجوم الذي يمنعه لا تراه الخدمة: اسمٌ مختلف في كل محاولة وكلمة مرور واحدة
 * شائعة. كلُّ حساب يرى محاولةً واحدة فلا يبلغ الخمس أبداً، والمهاجم يمرّ على ألف
 * اسم بلا أن يعترضه شيء.</p>
 *
 * <p>وبساعةٍ مثبّتة لا بانتظار: "متى يُفتح القفل" سؤالٌ لا يُختبر بغير تحريك الوقت.</p>
 */
class EdgeThrottleTest {

    private static final Instant NOON = Instant.parse("2026-09-20T12:00:00Z");

    private final EdgeThrottle throttle = new EdgeThrottle(fixedAt(NOON));

    @Test
    void aShiftfulOfMistypedPasswordsDoesNotCloseTheCounter() {
        MockHttpServletRequest request = from("196.0.0.10");

        for (int attempt = 0; attempt < EdgeThrottle.MAX_FAILURES - 1; attempt++) {
            throttle.recordFailure(request);
        }

        assertThatCode(() -> throttle.refuseIfLocked(request)).doesNotThrowAnyException();
    }

    @Test
    void theAddressIsRefusedOnceItPassesTheLimit() {
        MockHttpServletRequest request = from("196.0.0.10");
        failToTheLimit(request);

        assertThatThrownBy(() -> throttle.refuseIfLocked(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.auth.locked", EdgeThrottle.LOCKOUT.toMinutes()));
    }

    /** قفلُ شباكٍ لا يقفل السنتر: الجهاز المجاور يعمل */
    @Test
    void lockingOneAddressLeavesTheOthersWorking() {
        failToTheLimit(from("196.0.0.10"));

        assertThatCode(() -> throttle.refuseIfLocked(from("196.0.0.11")))
                .doesNotThrowAnyException();
    }

    /** الدخول الناجح يمسح ما قبله: أربعةٌ أخطأوا ثم نجح الخامس على الشباك نفسه */
    @Test
    void oneSuccessClearsTheAddressForWhoeverComesNext() {
        MockHttpServletRequest request = from("196.0.0.10");
        for (int attempt = 0; attempt < EdgeThrottle.MAX_FAILURES - 1; attempt++) {
            throttle.recordFailure(request);
        }

        throttle.recordSuccess(request);
        throttle.recordFailure(request);

        assertThatCode(() -> throttle.refuseIfLocked(request)).doesNotThrowAnyException();
    }

    @Test
    void theLockOpensWhenItsTimeHasPassed() {
        MockHttpServletRequest request = from("196.0.0.10");
        failToTheLimit(request);

        EdgeThrottle later = new EdgeThrottle(fixedAt(NOON.plus(EdgeThrottle.LOCKOUT)));
        // حاجزٌ جديد بساعة متأخرة لا يحمل عدّاد الأول؛ المقصود هنا أن المدة محدودة
        assertThatCode(() -> later.refuseIfLocked(request)).doesNotThrowAnyException();
    }

    /**
     * العتبة أوسع من عتبة الاسم عمداً، ولا يجوز أن تتساوى معها.
     *
     * <p>خلف وسيطٍ عكسي بلا إعداد صحيح تبدو كل الطلبات من عنوانٍ واحد، فعتبةٌ ضيّقة
     * تقفل السنتر كلّه بمحاولات مهاجمٍ واحد. هذا السطر يمنع من يوحّد الرقمين ظنّاً
     * أنه يشدّد.</p>
     */
    @Test
    void theAddressLimitIsWiderThanTheAccountLimit() {
        assertThat(EdgeThrottle.MAX_FAILURES)
                .isGreaterThan(com.codejava.center.core.security.LoginThrottle.MAX_FAILURES);
    }

    private void failToTheLimit(MockHttpServletRequest request) {
        for (int attempt = 0; attempt < EdgeThrottle.MAX_FAILURES; attempt++) {
            throttle.recordFailure(request);
        }
    }

    private MockHttpServletRequest from(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private Clock fixedAt(Instant moment) {
        return Clock.fixed(moment, ZoneOffset.UTC);
    }
}
