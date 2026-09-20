package com.codejava.center.core.phone;

import java.util.Optional;

/**
 * توحيد صيغة أرقام الهواتف إلى الصيغة الدولية بلا رموز (مثال: 201012345678).
 *
 * <p>الأرقام تُدخل يدوياً في شاشة تسجيل الطلاب بصيغ مختلفة: بصفر في البداية، أو بمقدمة
 * دولية، أو بمسافات وشرطات. إرسال رقم بصيغة خاطئة يفشل بصمت أو يصل شخصاً آخر، لذلك
 * التحقق هنا صارم ويُرجع {@link Optional} فارغاً بدل تخمين رقم غير مؤكد.</p>
 *
 * <p>والبلد معامل لا ثابت مكتوب في الكود: راجع {@link PhoneCountry}. الدوال بلا بلد
 * تعني {@link PhoneCountry#EGYPT}، وهو ما كانت تعنيه دائماً.</p>
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static Optional<String> toInternational(String rawNumber) {
        return toInternational(rawNumber, PhoneCountry.EGYPT);
    }

    /**
     * @return الرقم بالصيغة الدولية، أو {@link Optional} فارغ إن كان غير صالح
     */
    public static Optional<String> toInternational(String rawNumber, PhoneCountry country) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return Optional.empty();
        }

        String digits = rawNumber.replaceAll("\\D", "");

        // 00201012345678 -> 201012345678
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }

        String code = country.dialCode();

        // 201012345678 (مقدمة دولية موجودة بالفعل)
        if (digits.startsWith(code) && digits.length() == code.length() + country.nationalDigits()) {
            String body = digits.substring(code.length());
            return country.isMobileBody(body) ? Optional.of(digits) : Optional.empty();
        }

        // 01012345678 -> 201012345678
        if (digits.startsWith("0") && digits.length() == country.nationalDigits() + 1) {
            String body = digits.substring(1);
            return country.isMobileBody(body) ? Optional.of(code + body) : Optional.empty();
        }

        // 1012345678 (بلا صفر ولا مقدمة)
        if (digits.length() == country.nationalDigits() && country.isMobileBody(digits)) {
            return Optional.of(code + digits);
        }

        return Optional.empty();
    }

    public static boolean isValid(String rawNumber) {
        return toInternational(rawNumber).isPresent();
    }

    public static boolean isValid(String rawNumber, PhoneCountry country) {
        return toInternational(rawNumber, country).isPresent();
    }
}
