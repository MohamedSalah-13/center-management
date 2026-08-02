package com.codejava.center.service.notification;

import java.util.Optional;

/**
 * توحيد صيغة أرقام الهواتف المصرية إلى الصيغة الدولية بلا رموز (مثال: 201012345678).
 *
 * <p>الأرقام تُدخل يدوياً في شاشة تسجيل الطلاب بصيغ مختلفة: بصفر في البداية، أو
 * بمقدمة دولية، أو بمسافات وشرطات. إرسال رقم بصيغة خاطئة يفشل بصمت أو يصل شخصاً
 * آخر، لذلك التحقق هنا صارم ويُرجع Optional فارغاً بدل تخمين رقم غير مؤكد.</p>
 */
public final class PhoneNumbers {

    private static final String EGYPT_CODE = "20";

    private PhoneNumbers() {
    }

    /**
     * @return الرقم بالصيغة الدولية، أو Optional فارغ إن كان غير صالح
     */
    public static Optional<String> toInternational(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return Optional.empty();
        }

        String digits = rawNumber.replaceAll("\\D", "");

        // 00201012345678 -> 201012345678
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }

        // 201012345678 (مقدمة دولية موجودة بالفعل)
        if (digits.startsWith(EGYPT_CODE) && digits.length() == 12) {
            return isValidMobileBody(digits.substring(2)) ? Optional.of(digits) : Optional.empty();
        }

        // 01012345678 -> 201012345678
        if (digits.startsWith("0") && digits.length() == 11) {
            String body = digits.substring(1);
            return isValidMobileBody(body) ? Optional.of(EGYPT_CODE + body) : Optional.empty();
        }

        // 1012345678 (بلا صفر ولا مقدمة)
        if (digits.length() == 10 && isValidMobileBody(digits)) {
            return Optional.of(EGYPT_CODE + digits);
        }

        return Optional.empty();
    }

    /** جسم رقم المحمول المصري: عشرة أرقام تبدأ بـ 10 أو 11 أو 12 أو 15 */
    private static boolean isValidMobileBody(String body) {
        return body.length() == 10
                && body.startsWith("1")
                && "0125".indexOf(body.charAt(1)) >= 0;
    }

    public static boolean isValid(String rawNumber) {
        return toInternational(rawNumber).isPresent();
    }
}
