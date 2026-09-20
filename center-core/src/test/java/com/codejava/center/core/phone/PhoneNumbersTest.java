package com.codejava.center.core.phone;

import org.junit.jupiter.api.Test;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * توحيد أرقام الهواتف: رقم بصيغة خاطئة يفشل إرساله بصمت أو يصل شخصاً آخر،
 * وكلاهما لا يظهر في أي اختبار آخر.
 */
class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "01012345678,  201012345678",   // الصيغة المحلية المعتادة
            "01112345678,  201112345678",
            "01212345678,  201212345678",
            "01512345678,  201512345678",
            "201012345678, 201012345678",   // دولي بالفعل
            "00201012345678, 201012345678", // بمقدمة 00
            "+20 101 234 5678, 201012345678", // بمسافات ورمز +
            "010-1234-5678, 201012345678",  // بشرطات
            "1012345678,   201012345678"    // بلا صفر ولا مقدمة
    })
    void normalizesEgyptianMobileNumbers(String input, String expected) {
        assertThat(PhoneNumbers.toInternational(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0221234567",     // أرضي لا محمول
            "0131234567",     // شبكة غير موجودة
            "010123456",      // أقصر من اللازم
            "0101234567890",  // أطول من اللازم
            "abcd",
            "   "
    })
    void rejectsInvalidNumbers(String input) {
        assertThat(PhoneNumbers.toInternational(input)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(PhoneNumbers.toInternational(null)).isEmpty();
        assertThat(PhoneNumbers.isValid(null)).isFalse();
    }

    /**
     * بلد آخر: نفس القواعد بأرقام أخرى.
     *
     * <p>كانت مصر مكتوبة في الكود، فرقمٌ سعودي صحيح يُرفض بلا سبب يفهمه أحد - ووليُّ أمرٍ
     * لا يصله شيء. ما يُفحص هنا أن البلد صار قيمةً تُمرَّر فعلاً، لا أن مصر أُعيدت كتابتها
     * بشكل آخر: الرقم السعودي يُقبل بقواعد بلده، ويُرفض بقواعد مصر، والعكس.</p>
     */
    @Test
    void appliesTheRulesOfTheCountryItIsGiven() {
        PhoneCountry saudiArabia = new PhoneCountry("966", 9, List.of("50", "53", "55"));

        assertThat(PhoneNumbers.toInternational("0501234567", saudiArabia)).contains("966501234567");
        assertThat(PhoneNumbers.toInternational("966501234567", saudiArabia)).contains("966501234567");

        assertThat(PhoneNumbers.toInternational("0501234567")).as("بقواعد مصر").isEmpty();
        assertThat(PhoneNumbers.toInternational("01012345678", saudiArabia)).as("بقواعد السعودية").isEmpty();
    }
}
