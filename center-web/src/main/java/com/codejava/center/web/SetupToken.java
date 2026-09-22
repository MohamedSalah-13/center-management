package com.codejava.center.web;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * برهانُ من نشر الخادم، لتهيئة حساب المدير الأوّل.
 *
 * <p>ليس حساباً - لا صفَّ له في {@code users}، ولا جلسةَ تحمله - تماماً كرمز
 * {@link PlatformOperator}. والفرقُ بينهما في السؤال الذي يجيبه كلٌّ منهما: ذاك "من
 * يملك المنصة"، وهذا "من نشر <b>هذا</b> الخادم". ولذلك متغيّران لا واحد: توحيدُهما
 * يجعل تسليمَ رمز التهيئة لعميلٍ يفتح سنترَه بنفسه تسليماً لسلطةٍ على السناتر كلها.</p>
 *
 * <p>والمقارنةُ وقراءةُ الترويسة في {@link BearerToken}، لا منسوختين هنا.</p>
 */
@Component
public class SetupToken {

    static final String TOKEN = "CENTER_SETUP_TOKEN";

    private final BearerToken token;

    public SetupToken(Environment environment) {
        this.token = BearerToken.from(environment, TOKEN);
    }

    public boolean authorises(String authorizationHeader) {
        return token.authorises(authorizationHeader);
    }

    /** أمضبوطٌ أصلاً؟ تقرؤها الصفحةُ لتقول "اضبط المتغيّر" بدل أن تعرض نموذجاً عقيماً */
    public boolean configured() {
        return token.configured();
    }
}
