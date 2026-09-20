package com.codejava.center.service;

import com.codejava.center.core.security.LoginThrottle;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * التحقق من هوية من يحاول الدخول.
 *
 * <h2>حاجزان، وكلٌّ منهما يغلق باباً مختلفاً</h2>
 *
 * <p><b>القفل بعد تكرار الفشل</b> ({@link LoginThrottle}). BCrypt يبطئ المحاولة الواحدة
 * ولا يمنع مليوناً، وسطرُ {@code LOGIN_FAILED} في سجل المراقبة يخبر صاحب السنتر بعد أن
 * يقع. القفل بالاسم هنا لأنه ما تعرفه هذه الخدمة؛ القفل بعنوان الشبكة - الذي يمنع من
 * يجرّب مئة اسم بكلمة واحدة - مكانه الحافة التي تعرف العنوان، ولا وجود لها بعد.</p>
 *
 * <p><b>وزمنٌ واحد للجوابين.</b> كان اسمٌ غير موجود يعود فوراً بينما كلمةٌ خاطئة على
 * حساب قائم تنتظر BCrypt، والفرق بين الزمنين يُقاس: من يجرّب ألف اسم يعرف أيُّها حساب
 * فعلي قبل أن يعرف كلمته. فصار الاسمُ المجهول يقابَل ببصمةٍ وهمية - نفس العمل ونفس
 * الزمن - ولا يبقى في الردّ ما يفرّق.</p>
 *
 * <p>والتفريق يبقى في <b>سجل المراقبة</b>: "اسم غير موجود" تخمينٌ و"كلمة خاطئة على حساب
 * قائم" محاولةٌ على شخص بعينه، والفرق هو ما يفيد صاحب السنتر. ما يُمنع هو تسريبه إلى من
 * يحاول، لا تسجيله لمن يملك.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LoginThrottle loginThrottle;

    /** ساعة البرنامج: "متى يُفتح القفل" سؤالٌ لا يُختبر بغير تحريك الوقت */
    private final Clock clock;

    /**
     * بصمةٌ لا يطابقها شيء، تُقابَل بها الأسماء المجهولة.
     *
     * <p>تُحسب مرةً عند أول استعمال لا تُكتب ثابتةً في الكود: بصمة BCrypt تحمل عدد
     * دوراتها، وواحدةٌ مكتوبة بعشر دورات بينما الإعداد على اثنتي عشرة تجعل الجواب
     * الوهمي أسرع من الحقيقي - وهو الفرق نفسه الذي وُجدت لتمحوه.</p>
     */
    private volatile String decoyHash;

    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        String key = throttleKey(username);
        Instant now = clock.instant();

        Duration locked = loginThrottle.remaining(key, now);
        if (locked.isPositive()) {
            // يُسجَّل كل رفضٍ أثناء القفل: مئة سطرٍ متتالٍ هي صورة المحاولة، وبلا
            // تسجيلها يبدو الهجوم وكأنه توقّف عند المحاولة الخامسة
            auditService.recordAs(username, null, AuditAction.LOGIN_FAILED, false,
                    "reason=locked-out; remainingMinutes=" + Math.max(1, locked.toMinutes()));
            throw new IllegalStateException(I18n.format("error.auth.locked",
                    Math.max(1, locked.toMinutes())));
        }

        Optional<User> userOpt = userRepository.findByUsername(username);

        // يُنفَّذ في الحالتين: المقارنة نفسها بالزمن نفسه، سواء وُجد الحساب أو لم يوجد
        boolean matches = passwordEncoder.matches(
                password == null ? "" : password,
                userOpt.map(User::getPassword).orElseGet(this::decoyHash));

        if (matches && userOpt.isPresent()) {
            loginThrottle.recordSuccess(key);
            loginThrottle.forgetExpired(now);

            User user = userOpt.get();
            auditService.recordAs(user.getUsername(), user.getRole(),
                    AuditAction.LOGIN_SUCCEEDED, true, null);
            return user;
        }

        loginThrottle.recordFailure(key, now);

        // السبب يُفصَّل هنا ولا يصل إلى من يحاول: اسم غير موجود يعني تخميناً، وكلمة
        // مرور خاطئة على حساب قائم تعني محاولة على حساب بعينه - والفرق بينهما هو ما
        // يفيد صاحب السنتر
        auditService.recordAs(username, userOpt.map(User::getRole).orElse(null),
                AuditAction.LOGIN_FAILED, false,
                userOpt.isPresent() ? "reason=wrong-password" : "reason=unknown-user");

        throw new IllegalArgumentException(I18n.get("error.auth.invalidCredentials"));
    }

    /**
     * يسجّل خروج مستخدم.
     *
     * <p>يأخذ المستخدم صراحةً لا من {@code UserSession}: شاشة لوحة القيادة تُفرغ الجلسة
     * أولاً حتى لا يرث المستخدم التالي صلاحيات السابق، فلا يبقى ما يُنسب إليه الحدث.</p>
     */
    public void recordLogout(User user) {
        if (user == null) {
            return;
        }
        auditService.recordAs(user.getUsername(), user.getRole(), AuditAction.LOGGED_OUT, true, null);
    }

    /**
     * المفتاح بحالة حرفٍ موحّدة: {@code Admin} و{@code admin} حسابٌ واحد في MySQL
     * بترتيب المقارنة الافتراضي، وعدّادان هنا يعني ضعفَ المسموح لمن يكتبه بحالتين.
     */
    private String throttleKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String decoyHash() {
        String current = decoyHash;
        if (current == null) {
            current = passwordEncoder.encode("no user by that name");
            decoyHash = current;
        }
        return current;
    }
}
