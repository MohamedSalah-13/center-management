package com.codejava.center.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * ما يُطلب حفظه من بيانات معلم.
 *
 * <p>أربعة حقول، وكلُّها حقيقةً ما تملكه شاشة المعلمين. والفائدة هنا ليست في حقلٍ غائب
 * بل في أن الكيان لم يعد يعبر الشبكة: {@code Teacher} في جسم طلبٍ يحمل معرّفه، فتصير
 * "احفظ المعلم رقم 3" جملةً يكتبها المُرسِل لا المسار.</p>
 *
 * <p>و{@code commissionValue} يُقرأ مع {@code commissionType}: نسبةً مئوية، أو مبلغاً
 * ثابتاً للحصة، أو إيجارَ قاعة يُخصم من إيرادها - انظر {@code util/CommissionTypes}.
 * الرقمُ وحده لا يقول أيَّها، ولذلك لا يُغيَّر أحدهما دون الآخر.</p>
 *
 * @param id     المعلم المُعدَّل، أو {@code null} لمعلم جديد
 */
public record TeacherDraft(
        Long id,

        @NotBlank
        @Size(max = 100)
        String name,

        @jakarta.validation.constraints.NotNull
        Long subjectId,

        @Size(max = 20)
        String commissionType,

        BigDecimal commissionValue) {

    public String trimmedName() {
        return name == null ? null : name.trim();
    }

    public boolean isNew() {
        return id == null;
    }
}
