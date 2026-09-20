package com.codejava.center.tenancy;

import com.codejava.center.platform.InviteCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * رمز الدعوة: ما يفتح حساب مدير سنترٍ كامل.
 *
 * <p>ما يُفحص هنا ليس أن الرمز "يعمل" بل أن ما يُحفظ منه لا يُعيد بناءه، وأن ما يكتبه
 * إنسان في هاتف يُقبل كما يكتبه - وكلاهما خطأٌ لا يظهر إلا عند من يستعمله.</p>
 */
class InviteCodeTest {

    @Test
    void twoCodesAreNeverTheSame() {
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < 500; index++) {
            codes.add(InviteCode.generate());
        }
        assertThat(codes).hasSize(500);
    }

    /**
     * الرمز يُنطق في هاتف ويُنسخ بخط اليد: من كتبه بحروف صغيرة أو بلا شرطات لا يُقال
     * له "رمز خاطئ" عن رمز صحيح.
     */
    @Test
    void theFingerprintIgnoresDashesAndLetterCase() {
        String code = InviteCode.generate();

        assertThat(InviteCode.fingerprint(code.toLowerCase()))
                .isEqualTo(InviteCode.fingerprint(code));
        assertThat(InviteCode.fingerprint(code.replace("-", "")))
                .isEqualTo(InviteCode.fingerprint(code));
        assertThat(InviteCode.fingerprint(" " + code + " "))
                .isEqualTo(InviteCode.fingerprint(code));
    }

    /** رمزان مختلفان ببصمتين: وإلا فتح رمزٌ مؤسسةَ غيره */
    @Test
    void differentCodesGiveDifferentFingerprints() {
        assertThat(InviteCode.fingerprint(InviteCode.generate()))
                .isNotEqualTo(InviteCode.fingerprint(InviteCode.generate()));
    }

    /** البصمة هي ما يُحفظ، فلا يجوز أن يظهر فيها الرمز */
    @Test
    void theFingerprintDoesNotCarryTheCode() {
        String code = InviteCode.generate();
        String fingerprint = InviteCode.fingerprint(code);

        assertThat(fingerprint).hasSize(64).doesNotContainIgnoringCase(code.replace("-", ""));
        assertThat(fingerprint).matches("[0-9a-f]{64}");
    }

    @Test
    void refusesNothingAtAll() {
        assertThatThrownBy(() -> InviteCode.fingerprint(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InviteCode.fingerprint("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
