package com.codejava.center.web.api;

import com.codejava.center.domain.User;
import com.codejava.center.security.AccessDeniedException;
import com.codejava.center.service.InitialSetupService;
import com.codejava.center.util.I18n;
import com.codejava.center.web.EdgeThrottle;
import com.codejava.center.web.SetupToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * حسابُ المدير الأوّل، على خادمٍ يخدم سنتراً واحداً.
 *
 * <h2>الثقب الذي يسدّه</h2>
 *
 * <p>{@link InitialSetupService} موجودٌ منذ أول إصدار ولم تكن له نقطةُ HTTP قطّ. فخادمٌ
 * لسنترٍ واحد على قاعدةٍ جديدة كان <b>يُقلع ويعمل ويخدم الطلبات، ولا يستطيع أحدٌ الدخول
 * إليه</b>: جدولُ المستخدمين فارغ، وشاشةُ التهيئة الوحيدة في البرنامج المكتبي. والحيلةُ
 * كانت توجيهَ البرنامج المكتبي إلى القاعدة نفسها مرةً واحدة - أي أن نشرَ الخادم كان
 * يحتاج إلى الجهاز، وهو نقضُ الغرض منه.</p>
 *
 * <h2>ولماذا رمزٌ، ولم يكفِ أنّ الجدول فارغ</h2>
 *
 * <p>على جهازٍ في سنتر، فراغُ جدول المستخدمين برهانٌ كافٍ: من يجلس أمام الجهاز الذي يحمل
 * القاعدة هو صاحبها. <b>وعلى خادمٍ لا يبرهن الفراغ شيئاً</b> - يكفي أن يبلغ أحدُهم
 * المنفذَ في النافذة بين إنشاء القاعدة وأول تهيئة ليصير مديرَ السنتر. وهو نفسُ السطر
 * الذي وُجدت رموزُ الدعوة على المنصة لأجله، والمنطقُ لا يتغيّر بعدد السناتر: البرهانُ
 * يُسلَّم خارج القناة.</p>
 *
 * <p>فالرمز {@code CENTER_SETUP_TOKEN} من البيئة، يملكه من نشر الخادم وحده، ويُقارَن في
 * زمنٍ ثابت. <b>وغيابُه يمنع</b>: نشرٌ لم يُضبط فيه لا يُهيَّأ من الويب أصلاً، بدل أن
 * يكون مفتوحاً لمن سبق.</p>
 *
 * <p>والفراغُ يبقى شرطاً <b>معه</b> لا بدلاً منه: الرمز يبقى حيّاً في بيئة الخادم بعد
 * التهيئة، وبلا فحص الفراغ يصير بابَ إنشاءٍ دائماً لمن قرأ متغيّرات التشغيل. ترتيبُهما
 * في {@code createInitialAdmin} هو الحارس، والقيدُ في القاعدة خلفه لنسختين تعملان معاً.</p>
 *
 * <h2>ولا وجود له على منصة</h2>
 *
 * <p>الشرطُ مرآةُ {@code SingleCentreConfig}، ولسببٍ قاطع: على منصةٍ لا يُعرف أيُّ سنترٍ
 * يُهيَّأ قبل جلسة، ورمزُ الدعوة - المربوط بسنترٍ بعينه - هو البرهانُ هناك. ونقطةٌ تعمل
 * في الشكلين ببرهانين مختلفين هي بابان تحت اسمٍ واحد.</p>
 */
@RestController
@RequestMapping("/api/setup")
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled",
        havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class SetupController {

    private final InitialSetupService setup;

    private final SetupToken token;

    private final EdgeThrottle edgeThrottle;

    /**
     * @param required أثمّة تهيئةٌ منتظرة
     * @param possible أمضبوطٌ الرمز أصلاً - فتقول الصفحةُ "اضبط المتغيّر" بدل أن تعرض
     *                 نموذجاً يُرفض كلُّ ما يُرسَل منه
     */
    public record State(boolean required, boolean possible) {
    }

    public record SetupRequest(@NotBlank String password, @NotBlank String confirmation) {
    }

    public record AdminView(String username) {
    }

    /**
     * أتحتاج هذه القاعدةُ مديراً؟
     *
     * <p>مفتوحةٌ بلا رمز: الصفحةُ تسألها قبل أن تعرض شيئاً، ولا تكشف إلا أن القاعدة
     * جديدة - وهو ما لا ينفع من لا يملك الرمز. وطلبُ الرمز هنا يعني أن تطلبه الصفحةُ
     * من كل زائر قبل أن تعرف أنّ ثمّة ما يُهيَّأ.</p>
     */
    @GetMapping
    public State state() {
        return new State(setup.isSetupRequired(), token.configured());
    }

    /**
     * يُنشئ {@code admin}.
     *
     * <p>و{@link EdgeThrottle} عليه لأن ثمّة ما يُخمَّن الآن: الرمز. وهو الفرق عن نقطةٍ
     * لا برهانَ فيها، حيث لا يمنع الخانقُ شيئاً.</p>
     */
    @PostMapping
    public AdminView createInitialAdmin(@Valid @RequestBody SetupRequest request,
                                        HttpServletRequest http) {
        edgeThrottle.refuseIfLocked(http);

        if (!token.authorises(http.getHeader(HttpHeaders.AUTHORIZATION))) {
            edgeThrottle.recordFailure(http);
            throw new AccessDeniedException(I18n.get("error.setup.tokenRequired"));
        }

        User admin = setup.createInitialAdmin(request.password(), request.confirmation());
        edgeThrottle.recordSuccess(http);
        return new AdminView(admin.getUsername());
    }
}
