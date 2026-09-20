package com.codejava.center.web;

import com.codejava.center.core.security.LoginThrottle;
import com.codejava.center.util.I18n;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * الحاجز الذي لا يعرفه إلا من يعرف عنوان الطالب.
 *
 * <p>{@code AuthService} يقفل الحساب بعد خمس محاولات، وهو يمنع من يجرّب كلمات المرور على
 * حسابٍ بعينه. ولا يمنع العكس: <b>اسمٌ مختلف في كل محاولة وكلمة مرور واحدة شائعة</b> -
 * فكلُّ حساب يرى محاولةً واحدة ولا يبلغ الخمس أبداً، بينما يمرّ المهاجم على ألف اسم. وذلك
 * الهجوم لا يُرى إلا من موضعٍ يعرف أن الطلبات كلها من مصدرٍ واحد.</p>
 *
 * <p>والمرحلة 3 أجّلت هذا بنصٍّ صريح - "القفل بعنوان الشبكة مكانه الحافة التي تعرف
 * العنوان، ولا وجود لها بعد" - وهذه هي الحافة.</p>
 *
 * <h2>العتبة أوسع بكثير، ولها سببان</h2>
 *
 * <p>الأول أن المفتاح يعدّ شيئاً آخر: خمس محاولات على حساب تخمينٌ ظاهر، وخمسٌ من عنوانٍ
 * واحد هي موظفٌ نسي كلمته ثم زميله على الجهاز نفسه.</p>
 *
 * <p>والثاني أخطر ويُكتب صراحةً: <b>خلف وسيطٍ عكسي بلا إعداد صحيح تبدو كل الطلبات من
 * عنوانٍ واحد</b> - عنوان الوسيط - فقفلٌ ضيّق يقفل السنتر كلّه بمحاولات مهاجمٍ واحد.
 * ولذلك {@code server.forward-headers-strategy} مذكورٌ في
 * {@code application.properties} بعواقب كل خيار، والعتبة هنا سخيّة بحيث يتحوّل الإعداد
 * المنسيّ إلى "يقفل نادراً" لا إلى "يقفل الجميع".</p>
 *
 * <p>ولا يُقرأ {@code X-Forwarded-For} هنا بيدٍ: ترويسةٌ يكتبها من يطلب تجعل تبديلَ سطرٍ
 * في الطلب تهرّباً من الحاجز - أو، أسوأ، قفلاً لعنوان إنسانٍ آخر. من يثق بها هو التركيب،
 * عبر ذلك الإعداد، حين يكون خلفه وسيطٌ يملكه صاحب النشر.</p>
 */
@Component
public class EdgeThrottle {

    /** ثلاثون: خمسةٌ لكلّ ستة أشخاص على شباكٍ واحد، وهو أكثر مما يقع في سنتر */
    static final int MAX_FAILURES = 30;

    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final LoginThrottle throttle = new LoginThrottle(MAX_FAILURES, LOCKOUT);

    private final Clock clock;

    public EdgeThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * يُسقط الطلب إن كان العنوان مقفولاً.
     *
     * <p>{@code IllegalStateException} لا {@code IllegalArgumentException}: ما يمنع
     * المحاولة حالٌ في الخادم لا خطأٌ فيما كُتب، فيصل كـ {@code 409} لا {@code 400} -
     * وهو ما يفرّق للمستخدم بين "تحقّق مما أدخلت" و"انتظر ربع ساعة".</p>
     */
    public void refuseIfLocked(HttpServletRequest request) {
        Duration locked = throttle.remaining(keyOf(request), clock.instant());
        if (locked.isPositive()) {
            throw new IllegalStateException(
                    I18n.format("error.auth.locked", Math.max(1, locked.toMinutes())));
        }
    }

    public void recordFailure(HttpServletRequest request) {
        throttle.recordFailure(keyOf(request), clock.instant());
    }

    /**
     * النجاح يمسح عدّاد العنوان.
     *
     * <p>ومن يبلغ الثلاثين لا ينقذه دخولٌ ناجح لأنه مقفول قبل أن يحاول - وهذا هو
     * المقصود: المسح يخدم الشباك الذي أخطأ فيه أربعةٌ ثم نجح الخامس.</p>
     */
    public void recordSuccess(HttpServletRequest request) {
        throttle.recordSuccess(keyOf(request));
        throttle.forgetExpired(clock.instant());
    }

    /**
     * العنوان كما تراه الحاوية.
     *
     * <p>{@code getRemoteAddr} لا ترويسة: هي الطرفُ الذي فتح الاتصال فعلاً. وخلف وسيطٍ
     * عكسي تعيد عنوانَ الوسيط ما لم يُضبط {@code server.forward-headers-strategy}، وعندها
     * يتولّى الإطارُ قراءةَ الترويسة ويعود هذا السطر صحيحاً بلا تغيير فيه.</p>
     */
    private String keyOf(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
