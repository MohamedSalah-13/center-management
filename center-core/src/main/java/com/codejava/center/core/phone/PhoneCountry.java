package com.codejava.center.core.phone;

import java.util.List;

/**
 * قواعد رقم المحمول في بلد واحد.
 *
 * <p>كانت مصر مكتوبة في الكود: مقدمةٌ "20" وطولٌ عشرة وبادئاتٌ أربع. وهي صحيحة للسنتر
 * الذي كُتب له البرنامج، وخاطئة للحظة التي يُباع فيها لسنتر في الرياض - ورقمٌ يُرفض
 * لأنه سعودي يعني وليَّ أمرٍ لا يصله شيء، بلا رسالة خطأ يفهمها أحد.</p>
 *
 * <p>ولذلك صارت القواعد قيمةً تُمرَّر: {@link #EGYPT} هو الافتراضي وما زال سلوكه حرفاً
 * بحرف كما كان، والخادم متعدّد المستأجرين يمرّر بلد كل مؤسسة.</p>
 *
 * @param dialCode       مقدمة الاتصال الدولية بلا {@code +}، مثل {@code "20"}
 * @param nationalDigits عدد أرقام الرقم الوطني بلا الصفر الأول ولا المقدمة
 * @param mobilePrefixes بادئات المحمول المقبولة في أول الرقم الوطني
 */
public record PhoneCountry(String dialCode, int nationalDigits, List<String> mobilePrefixes) {

    /** مصر: {@code 20}، وعشرة أرقام تبدأ بـ 10 أو 11 أو 12 أو 15 */
    public static final PhoneCountry EGYPT =
            new PhoneCountry("20", 10, List.of("10", "11", "12", "15"));

    public PhoneCountry {
        mobilePrefixes = List.copyOf(mobilePrefixes);
    }

    /** هل هذا جسم رقم محمول صالح في هذا البلد */
    public boolean isMobileBody(String body) {
        return body.length() == nationalDigits
                && mobilePrefixes.stream().anyMatch(body::startsWith);
    }
}
