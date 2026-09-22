package com.codejava.center.web;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * من يملك المنصة نفسها - لا من يعمل في سنترٍ عليها.
 *
 * <p>هويتان مختلفتان تماماً: موظفُ السنتر له حساب <b>داخل قاعدة مؤسسته</b>، ومشغّلُ
 * المنصة ليس في أيّ منها - هو من يفتحها. فلا جدول مستخدمين يصلح له، ولا جلسة سنتر
 * تحمله.</p>
 *
 * <p>ورمزٌ من بيئة التشغيل هو أبسط ما يصفه بصدق، ومن المصدر نفسه الذي تأتي منه بقية
 * أسرار الخادم ({@link EnvironmentSecrets}): يُحقن من خارج البرنامج فلا يدخل صورة
 * النشر، ويُبدَّل بإعادة تشغيل لا بإصدار.</p>
 *
 * <p><b>والغياب يعني المنع لا السماح.</b> نشرٌ نُسي فيه المتغيّر لا يفتح سناتر لمن يطلب،
 * بل لا يفتحها لأحد - والفرق بين الاتجاهين هو الفرق بين إزعاجٍ يُبلَّغ عنه في دقيقة
 * وبابٍ مفتوح لا يعرف أحد أنه مفتوح.</p>
 */
@Component
public class PlatformOperator {

    static final String TOKEN = "CENTER_PLATFORM_TOKEN";

    private final BearerToken token;

    public PlatformOperator(Environment environment) {
        this.token = BearerToken.from(environment, TOKEN);
    }

    /**
     * المقارنة بزمنٍ ثابت - في {@link BearerToken}، مع بابِ التهيئة.
     *
     * <p>{@code String.equals} يخرج عند أول حرف مختلف، والفرق في الزمن يُقاس - فيُبنى
     * الرمز حرفاً حرفاً. وهو هنا أهمّ منه في كلمة مرور: الرمز واحد للمنصة كلها، ومن
     * يبلغه يفتح سناتر ويوقف اشتراكات. ولذلك بالذات لا يُنسخ هذا السطر: بابٌ ثانٍ
     * يُكتب بـ{@code equals} لا شيء فيه يبدو مختلفاً.</p>
     */
    public boolean authorises(String authorizationHeader) {
        return token.authorises(authorizationHeader);
    }

    /** أثمّة رمزٌ مضبوط أصلاً؟ يقرؤها سجلّ الإقلاع ليقول إن سطح المنصة مغلق */
    public boolean configured() {
        return token.configured();
    }
}
