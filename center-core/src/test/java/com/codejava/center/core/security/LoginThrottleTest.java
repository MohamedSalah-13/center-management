package com.codejava.center.core.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * القفل بعد تكرار الفشل.
 *
 * <p>خطؤه صامت في الاتجاهين: قفلٌ لا يقع يترك كلمة المرور تُخمَّن بمعدّل الجهاز،
 * وقفلٌ لا يُفتح يحبس صاحب السنتر خارج سنتره في ذروة الحضور. ولا يُختبر أيٌّ منهما
 * بغير تحريك الوقت.</p>
 */
class LoginThrottleTest {

    private static final Instant NOON = Instant.parse("2026-09-20T12:00:00Z");

    private final LoginThrottle throttle = new LoginThrottle();

    @Test
    void aFewMistakesDoNotLockAnybodyOut() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES - 1; attempt++) {
            throttle.recordFailure("admin", NOON);
        }

        assertThat(throttle.isLocked("admin", NOON)).isFalse();
    }

    @Test
    void theLockFallsOnTheAttemptAfterTheLimit() {
        failTimes(LoginThrottle.MAX_FAILURES);

        assertThat(throttle.isLocked("admin", NOON)).isTrue();
        assertThat(throttle.remaining("admin", NOON)).isEqualTo(LoginThrottle.LOCKOUT);
    }

    /** القفل على اسمٍ بعينه: من يخمّن حساب المدير لا يقفل السكرتارية عن عملها */
    @Test
    void lockingOneAccountLeavesTheOthersWorking() {
        failTimes(LoginThrottle.MAX_FAILURES);

        assertThat(throttle.isLocked("admin", NOON)).isTrue();
        assertThat(throttle.isLocked("reception", NOON)).isFalse();
    }

    @Test
    void theLockOpensWhenItsTimeHasPassed() {
        failTimes(LoginThrottle.MAX_FAILURES);

        Instant later = NOON.plus(LoginThrottle.LOCKOUT);

        assertThat(throttle.isLocked("admin", later)).isFalse();
        assertThat(throttle.remaining("admin", later)).isZero();
    }

    /**
     * العدّاد يبدأ من جديد بعد القفل ولا يتراكم: بلا ذلك يجد من أخطأ خمساً اليوم
     * وخمساً بعد شهر نفسه مقفولاً من المحاولة الأولى.
     */
    @Test
    void theCountStartsOverAfterTheLockRatherThanAccumulating() {
        failTimes(LoginThrottle.MAX_FAILURES);
        Instant later = NOON.plus(LoginThrottle.LOCKOUT).plusSeconds(1);

        throttle.recordFailure("admin", later);

        assertThat(throttle.isLocked("admin", later)).isFalse();
    }

    /** من تذكّر كلمته لا يبقى قريباً من القفل */
    @Test
    void aSuccessfulSignInClearsWhatCameBefore() {
        failTimes(LoginThrottle.MAX_FAILURES - 1);

        throttle.recordSuccess("admin");
        throttle.recordFailure("admin", NOON);

        assertThat(throttle.isLocked("admin", NOON)).isFalse();
    }

    /** الخريطة لا تنمو بعدد الأسماء التي جُرّبت */
    @Test
    void whatIsOldIsForgotten() {
        throttle.recordFailure("guessed-name", NOON);

        throttle.forgetExpired(NOON.plus(LoginThrottle.LOCKOUT).plus(Duration.ofMinutes(1)));
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES - 1; attempt++) {
            throttle.recordFailure("guessed-name", NOON.plus(Duration.ofHours(1)));
        }

        assertThat(throttle.isLocked("guessed-name", NOON.plus(Duration.ofHours(1)))).isFalse();
    }

    /**
     * حاجزٌ بعتبةٍ أخرى لمفتاحٍ من طبيعة أخرى.
     *
     * <p>الحافة تقفل بعنوان الشبكة لا بالاسم، وذلك يعدّ شيئاً آخر: خمسٌ من عنوانٍ واحد
     * هي موظفٌ نسي كلمته ثم زميله، وقفلُ العنوان يقفل الشباك كلّه لا حساباً واحداً.</p>
     */
    @Test
    void aThrottleCanCountToAnotherLimit() {
        LoginThrottle wider = new LoginThrottle(30, LoginThrottle.LOCKOUT);

        for (int attempt = 0; attempt < 29; attempt++) {
            wider.recordFailure("196.0.0.1", NOON);
        }
        assertThat(wider.isLocked("196.0.0.1", NOON)).isFalse();

        wider.recordFailure("196.0.0.1", NOON);
        assertThat(wider.isLocked("196.0.0.1", NOON)).isTrue();
    }

    /** حاجزٌ بعتبةٍ صفر أو سالبة يقفل كل شيء أو لا يقفل شيئاً؛ كلاهما عطلٌ صامت */
    @Test
    void aThrottleRefusesToBeBuiltWithoutALimit() {
        assertThatThrownBy(() -> new LoginThrottle(0, LoginThrottle.LOCKOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginThrottle(5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void failTimes(int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            throttle.recordFailure("admin", NOON);
        }
    }
}
