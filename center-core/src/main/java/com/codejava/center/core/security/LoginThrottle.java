package com.codejava.center.core.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * متى يُمنع حسابٌ من المحاولة، ومتى يُفتح من جديد.
 *
 * <p>بلا هذا، كلمةُ مرور حسابٍ قائم تُخمَّن بمعدّل ما يحتمله الجهاز. BCrypt يبطئ
 * المحاولة الواحدة ولا يمنع مليوناً، وسطرُ {@code LOGIN_FAILED} في سجل المراقبة يخبر
 * صاحب السنتر <b>بعد</b> أن يقع - وبعدها لا يفيد.</p>
 *
 * <p>والمفتاح نصٌّ يختاره المستدعي: اسم المستخدم هنا لأنه ما تعرفه الخدمة، وعنوان
 * الشبكة عند حافةٍ تعرفه. الاثنان لازمان ويمنعان شيئين مختلفين - المفتاح بالاسم يحمي
 * حساباً بعينه من التخمين، والمفتاح بالعنوان يمنع من يجرّب مئة اسم بكلمة واحدة، وهو
 * ما لا يمسّ عدّاد أيّ اسم منها.</p>
 *
 * <p>في الذاكرة لا في القاعدة: القفل يجب أن يقع على المحاولة الخامسة لا بعد كتابةٍ
 * وقراءة، وكتابةُ كل محاولة فاشلة في القاعدة تجعل من يجرّب الكلمات يكتب في قرص
 * الخادم. وخادمان يعني عدّادين - يضاعف المسموح ولا يلغيه، ويُسدّ عند الحافة بالعنوان.</p>
 *
 * <p>نقيّ في النواة مع اختباره للسبب الذي جعل {@code BackupRetention} كذلك: قرارٌ
 * خطؤه صامت في الاتجاهين - قفلٌ لا يقع فلا يحمي، وقفلٌ لا يُفتح فيُحبس صاحب السنتر
 * خارج سنتره.</p>
 */
public final class LoginThrottle {

    /**
     * خمس محاولات. أكثر من ذلك يحتمله من نسي كلمته وأقلّ يحبس من يكتب بلوحة عربية.
     */
    public static final int MAX_FAILURES = 5;

    /**
     * ربع ساعة. طويلةٌ بما يجعل التخمين بلا معنى - عشرون محاولة في الساعة - وقصيرةٌ
     * بما لا يوقف سنتراً في ذروة الحضور حتى يجد أحدٌ رقم الدعم.
     */
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private final int maxFailures;

    private final Duration lockout;

    /** الحاجز على اسم المستخدم: القيمتان أعلاه */
    public LoginThrottle() {
        this(MAX_FAILURES, LOCKOUT);
    }

    /**
     * حاجزٌ بعتبةٍ أخرى، لمفتاحٍ من طبيعة أخرى.
     *
     * <p>الحاجز على الاسم والحاجز على عنوان الشبكة يعدّان شيئين مختلفين، فلا تصلح لهما
     * عتبةٌ واحدة: خمس محاولات على حسابٍ بعينه تخمينٌ ظاهر، وخمسٌ من عنوانٍ واحد هي
     * موظفٌ نسي كلمته ثم زميله - وقفلُ العنوان يقفل الشباك كلّه لا حساباً واحداً.</p>
     */
    public LoginThrottle(int maxFailures, Duration lockout) {
        if (maxFailures < 1 || lockout == null || lockout.isNegative()) {
            throw new IllegalArgumentException("a throttle needs a positive limit and a lockout");
        }
        this.maxFailures = maxFailures;
        this.lockout = lockout;
    }

    /** محاولاتُ مفتاحٍ واحد: كم فشلت، ومتى كان آخر فشل */
    private record Attempts(int failures, Instant lastFailure) {
    }

    /** أمقفولٌ هذا المفتاح الآن؟ */
    public boolean isLocked(String key, Instant now) {
        return remaining(key, now).isPositive();
    }

    /**
     * ما تبقّى من مدّة القفل، أو صفرٌ إن لم يكن مقفولاً.
     *
     * <p>تُعاد المدّة لا "نعم/لا" حتى تقول الشاشة كم يُنتظر: رسالةٌ تقول "مقفول" بلا
     * مدّة تُقرأ على أنها عطل دائم، فيُعاد تشغيل البرنامج ويُتصل بالدعم.</p>
     */
    public Duration remaining(String key, Instant now) {
        Attempts current = attempts.get(key);
        if (current == null || current.failures() < maxFailures) {
            return Duration.ZERO;
        }
        Duration passed = Duration.between(current.lastFailure(), now);
        Duration left = lockout.minus(passed);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /**
     * محاولةٌ فاشلة.
     *
     * <p>العدّاد يبدأ من جديد بعد انقضاء القفل لا يتراكم: تراكمُه يعني أن من أخطأ
     * خمساً اليوم وخمساً بعد شهر يجد نفسه مقفولاً من المحاولة الأولى.</p>
     */
    public void recordFailure(String key, Instant now) {
        attempts.compute(key, (ignored, current) -> {
            int failures = current == null || expired(current, now) ? 1 : current.failures() + 1;
            return new Attempts(failures, now);
        });
    }

    /** دخولٌ ناجح: يُمحى العدّاد، فمن تذكّر كلمته لا يبقى قريباً من القفل */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    /** يُنسى ما مضى وقتُه، وإلا نمت الخريطة بعدد الأسماء التي جُرّبت */
    public void forgetExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> expired(entry.getValue(), now));
    }

    private boolean expired(Attempts current, Instant now) {
        return Duration.between(current.lastFailure(), now).compareTo(lockout) >= 0;
    }
}
