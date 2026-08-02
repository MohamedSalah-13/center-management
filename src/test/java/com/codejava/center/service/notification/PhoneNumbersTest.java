package com.codejava.center.service.notification;

import org.junit.jupiter.api.Test;
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
}
