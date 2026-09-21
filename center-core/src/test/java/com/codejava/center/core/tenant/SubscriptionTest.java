package com.codejava.center.core.tenant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * متى ينقضي اشتراك، وإلى متى يمتدّ بالدفع.
 *
 * <p>الخطأُ هنا في اتجاهين، وكلاهما صامت: يوماً إلى الأمام فيُخدَم سنترٌ لم يدفع منذ
 * أشهر، ويوماً إلى الخلف فتُغلق أبوابُ سنترٍ دافع في صباح يومٍ دفع ثمنه - ولا يُكتشف
 * الثاني إلا من مكالمة غاضبة.</p>
 *
 * <p>ولا شيء هنا يحتاج قاعدةً ولا Spring: التاريخُ والمدةُ واليوم، وهي بالضبط حجّة
 * {@code BackupScheduleTest}.</p>
 */
class SubscriptionTest {

    private static final LocalDate PAID_THROUGH = LocalDate.of(2026, 9, 30);

    /**
     * <b>اليوم الأخير مدفوع.</b>
     *
     * <p>{@code paidThrough} آخرُ يومٍ يشمله الدفع لا أولُ يومٍ بعده. وخلطُ الاثنين
     * يسرق من كل سنترٍ يوماً في كل شهر - وهو فرقٌ لا يراه أحد في صفّ قاعدة.</p>
     */
    @Test
    void theLastPaidDayIsPaidAndSoIsTheWholeGraceWindow() {
        Subscription subscription = Subscription.of(PAID_THROUGH);

        assertThat(subscription.lapsed(PAID_THROUGH)).isFalse();
        assertThat(subscription.lapsed(PAID_THROUGH.plusDays(1))).isFalse();
        assertThat(subscription.lapsed(PAID_THROUGH.plusDays(Subscription.GRACE_DAYS)))
                .as("آخرُ أيام السماح ما زال مسموحاً")
                .isFalse();
        assertThat(subscription.lapsed(PAID_THROUGH.plusDays(Subscription.GRACE_DAYS + 1)))
                .as("وأولُ يومٍ بعدها هو الانقضاء")
                .isTrue();
    }

    /**
     * <b>وغيابُ التاريخ لا ينقضي أبداً.</b>
     *
     * <p>يعني "لا اشتراك يُتابَع": عمودٌ فارغ في كل صفٍّ لحظةَ الترقية، وخادمٌ لسنترٍ
     * واحد بلا فوترة، واتفاقٌ خاص. وقراءتُه "لم يدفع" تغلق المؤسسات كلها في اللحظة
     * التي تُرقّى فيها المنصة.</p>
     */
    @Test
    void anAbsentDateMeansNoSubscriptionIsTrackedNotOneThatExpiredLongAgo() {
        Subscription untracked = Subscription.of(null);

        assertThat(untracked.tracked()).isFalse();
        assertThat(untracked.lapsed(LocalDate.of(2099, 1, 1))).isFalse();
        assertThat(untracked.needsChasing(LocalDate.of(2099, 1, 1)))
                .as("ولا يُطالَب بما لم يُتَّفق عليه")
                .isFalse();
    }

    /** والباقي يُعدّ من الدفع لا من نهاية السماح: السماحُ احتياطٌ لا مدةٌ مباعة */
    @Test
    void daysRemainingCountToThePaidDateAndNotThroughTheGrace() {
        Subscription subscription = Subscription.of(PAID_THROUGH);

        assertThat(subscription.daysRemaining(PAID_THROUGH.minusDays(10))).isEqualTo(10);
        assertThat(subscription.daysRemaining(PAID_THROUGH)).isZero();
        assertThat(subscription.daysRemaining(PAID_THROUGH.plusDays(3)))
                .as("وداخل السماح يكون سالباً: هو دينٌ لا رصيد")
                .isEqualTo(-3);
    }

    /** والمطالبة تبدأ قبل الانقضاء، بنفس نافذة السماح لا برقمٍ ثانٍ يفترق عنها */
    @Test
    void chasingStartsBeforeItLapsesAndUsesTheSameWindow() {
        Subscription subscription = Subscription.of(PAID_THROUGH);

        assertThat(subscription.needsChasing(PAID_THROUGH.minusDays(Subscription.GRACE_DAYS + 1)))
                .isFalse();
        assertThat(subscription.needsChasing(PAID_THROUGH.minusDays(Subscription.GRACE_DAYS)))
                .isTrue();
        assertThat(subscription.needsChasing(PAID_THROUGH.plusDays(30)))
                .as("والمنقضي يبقى مطلوباً، لا يسقط من القائمة بانقضائه")
                .isTrue();
    }

    /**
     * <b>من دفع مبكراً لا يُسرق منه ما دفعه.</b>
     *
     * <p>التمديدُ من اليوم دائماً يعطي سنتراً باقياً له عشرة أيام ثلاثين لا أربعين -
     * عشرةُ أيام تختفي في كل دفعةٍ مبكرة، ولا سطرَ في أي مكان يقول إنها اختفت.</p>
     */
    @Test
    void payingEarlyAddsToWhatIsAlreadyPaidRatherThanReplacingIt() {
        LocalDate today = PAID_THROUGH.minusDays(10);

        assertThat(Subscription.of(PAID_THROUGH).extendedBy(1, today))
                .isEqualTo(PAID_THROUGH.plusMonths(1));
    }

    /**
     * <b>ومن دفع بعد انقضاءٍ طويل يشتري من اليوم.</b>
     *
     * <p>التمديدُ من {@code paidThrough} دائماً يشتري لسنترٍ منقضٍ منذ ثلاثة أشهر
     * شهراً مضى: يبقى مغلقاً بعد أن دفع، والمشغّلُ يراه في قائمته دافعاً - وهي أسوأ
     * حالةٍ ممكنة، إذ يُحسب العطلُ حلاً.</p>
     */
    @Test
    void payingAfterALongLapseBuysAMonthFromTodayNotOneThatAlreadyPassed() {
        LocalDate today = PAID_THROUGH.plusMonths(3);

        LocalDate extended = Subscription.of(PAID_THROUGH).extendedBy(1, today);

        assertThat(extended).isEqualTo(today.plusMonths(1));
        assertThat(Subscription.of(extended).lapsed(today))
                .as("والدفعُ يفتح الأبواب في الحال، لا بعد شهرين")
                .isFalse();
    }

    /** وأولُ دفعةٍ لسنترٍ لم يُفوتَر بعد تبدأ من اليوم */
    @Test
    void theFirstPaymentOfAnUntrackedCentreStartsToday() {
        LocalDate today = LocalDate.of(2026, 9, 21);

        assertThat(Subscription.of(null).extendedBy(3, today)).isEqualTo(today.plusMonths(3));
    }

    /**
     * والوحدةُ شهرٌ كامل، و{@code plusMonths} يقصر اليوم 31 إلى آخر الشهر الأقصر.
     *
     * <p>والبديلُ - ثلاثون يوماً - يزحف بتاريخ الانقضاء إلى الوراء شهراً بعد شهر حتى
     * يصير سنترٌ يدفع أولَ كل شهر منقضياً في منتصفه.</p>
     */
    @Test
    void wholeMonthsClampToTheShorterMonthRatherThanDriftingBackwards() {
        assertThat(Subscription.of(LocalDate.of(2026, 1, 31))
                .extendedBy(1, LocalDate.of(2026, 1, 20)))
                .isEqualTo(LocalDate.of(2026, 2, 28));

        assertThat(Subscription.of(LocalDate.of(2026, 1, 31))
                .extendedBy(12, LocalDate.of(2026, 1, 20)))
                .as("وسنةٌ كاملة تعود إلى اليوم نفسه، لا إلى ما قبله بخمسة أيام")
                .isEqualTo(LocalDate.of(2027, 1, 31));
    }

    /** وصفرُ شهورٍ أو سالبُها ليس دفعاً: يُردّ بدل أن يُقصّر اشتراكاً قائماً بصمت */
    @Test
    void anEmptyOrNegativePaymentIsRefusedRatherThanShorteningTheSubscription() {
        Subscription subscription = Subscription.of(PAID_THROUGH);
        LocalDate today = LocalDate.of(2026, 9, 21);

        assertThatThrownBy(() -> subscription.extendedBy(0, today))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscription.extendedBy(-1, today))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
