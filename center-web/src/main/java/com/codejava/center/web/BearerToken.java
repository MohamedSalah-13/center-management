package com.codejava.center.web;

import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * رمزٌ يأتي من البيئة ويُقارَن في زمنٍ ثابت.
 *
 * <p>يخدم بابين لا مستخدمَ وراءهما: رمزُ مشغّل المنصة، ورمزُ تهيئة أوّل مدير. وكلاهما
 * <b>ليس حساباً</b> - لا صفَّ له في جدول، ولا جلسةَ تحمله - بل برهانٌ يملكه من نشر
 * الخادم وحده.</p>
 *
 * <h2>ثلاثةُ أشياء يجمعها، وكلٌّ منها يُكتب خطأً حين يُنسخ</h2>
 *
 * <ul>
 *   <li><b>{@link MessageDigest#isEqual} لا {@code String.equals}</b>: الثانية تتوقف عند
 *       أول بايت مختلف، وزمنُها يُقاس، فيُستخرج الرمزُ حرفاً حرفاً. وهذه الرموز تفتح
 *       السناتر كلَّها.</li>
 *   <li><b>وغيابُ الرمز يمنع</b> ولا يسمح. نشرٌ نُسي فيه المتغيّر يُغلق الباب على
 *       الجميع بدل أن يفتحه للجميع - والثاني بابٌ لا يعرف أحدٌ أنه مفتوح، لأن كل شيء
 *       يبدو عاملاً.</li>
 *   <li><b>والترويسةُ تُقرأ بالسابقة {@code Bearer }</b> وحدها: رمزٌ عارٍ في الترويسة
 *       يمرّ عند صنفٍ ويُرفض عند آخر.</li>
 * </ul>
 *
 * <p>وهو صنفٌ واحد لا اثنان لأن نسخَ هذه الثلاثة هو بالضبط كيف يُكتب البابُ الثاني
 * بـ{@code equals} ولا شيء يقول ذلك.</p>
 */
final class BearerToken {

    private static final String BEARER = "Bearer ";

    /** {@code null} يعني "لم يُضبط"، وهو ما يمنع الجميع */
    private final byte[] expected;

    private BearerToken(byte[] expected) {
        this.expected = expected;
    }

    static BearerToken from(Environment environment, String variable) {
        String value = environment.getProperty(variable);
        return new BearerToken(value == null || value.isBlank()
                ? null
                : value.getBytes(StandardCharsets.UTF_8));
    }

    boolean configured() {
        return expected != null;
    }

    boolean authorises(String authorizationHeader) {
        if (expected == null || authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER)) {
            return false;
        }
        byte[] offered = authorizationHeader.substring(BEARER.length())
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, offered);
    }
}
