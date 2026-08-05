package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تعمية الأسرار المحفوظة على الجهاز.
 *
 * <p>يخدم سرَّين لهما نفس الخطر عند العطب: كلمة مرور النسخ الاحتياطية — ومن فقدها فقد
 * كل نسخه — ومفتاح مزوّد الرسائل. والخلل هنا صامت: قيمة تُحفظ ولا تُقرأ تظهر للمستخدم
 * كحقل فارغ، فيعيد كتابتها ظاناً أنه نسي.</p>
 */
class MachineSecretTest {

    private final MachineSecret secret = MachineSecret.forPurpose("test/purpose/v1");

    @Test
    void revealsWhatItConcealed() {
        String stored = secret.conceal("كلمة السر 123".toCharArray());

        assertThat(stored).doesNotContain("كلمة");
        assertThat(secret.reveal(stored)).containsExactly("كلمة السر 123".toCharArray());
    }

    /** كل حفظ بمتجه تهيئة جديد، وإلا دلّ تكرار النصّ المعمّى على تكرار القيمة */
    @Test
    void concealsTheSameValueDifferentlyEveryTime() {
        assertThat(secret.conceal("x".toCharArray()))
                .isNotEqualTo(secret.conceal("x".toCharArray()));
    }

    /** سرّ النسخ لا يُفكّ بمفتاح سرّ الرسائل ولو نُقلت القيمة بين الاثنين */
    @Test
    void aSecretOfAnotherPurposeCannotBeRead() {
        String stored = secret.conceal("x".toCharArray());

        assertThat(MachineSecret.forPurpose("other/purpose/v1").reveal(stored)).isNull();
    }

    /** قيمة تلفت أو نُسخت من سجل جهاز آخر: سرّ غير مضبوط، لا انهيار */
    @Test
    void returnsNullForACorruptedValueInsteadOfThrowing() {
        assertThat(secret.reveal("not-base64-at-all!!")).isNull();
        assertThat(secret.reveal("YWJjZGVmZ2hpamtsbW5vcA==")).isNull();
    }
}
