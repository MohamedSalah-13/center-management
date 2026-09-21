package com.codejava.center.service.dto;

import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import jakarta.validation.constraints.NotNull;

/**
 * ما يُطلب ضبطه في قاعدة تنبيه.
 *
 * <p>آخرُ المسودّات الخمس، وبها لا يبقى كيانُ JPA مدخلاً لأي كتابة. والقاعدةُ التي
 * تجمعها: <b>حقلٌ يدخل المسودة إن كانت الشاشةُ التي ترسلها تملكه</b>.</p>
 *
 * <p>و{@code updatedAt} و{@code updatedBy} ليسا فيها. تكتبهما الخدمةُ من
 * {@code CurrentActor}، وحقلٌ لهما هنا يعني أن من يضبط قاعدةً يكتب اسمَ غيره تحتها -
 * وهي بعينها الثغرة التي أُغلقت في سجل المراقبة يوم صارت {@code recordAs} حبيسةَ
 * حزمتها: سطرٌ يستطيع أحدٌ أن يكتب فيه اسم آخر ليس دليلاً.</p>
 *
 * <p>و{@code type} ليس معرّفاً يُختار بل مفتاحُ الصفّ: {@code AlertRuleRegistry} يبني
 * القائمة من {@code AlertType} في كل قراءة ولا يزرع الترحيلُ صفوفاً، فأولُ حفظٍ لنوعٍ
 * هو ما يكتب صفَّه. ولذلك لا معرّفَ في المسودة أصلاً - النوعُ يكفي.</p>
 *
 * <p>ووجهةُ الرسالة تُفحص في الخدمة لا هنا: نوعٌ لا يصلح للإرسال يُردّ إلى
 * {@code INTERNAL} بدل أن يُحفظ وعداً لا يتحقق - فشلُ نسخةٍ احتياطية لا يُرسل إلى
 * هاتف أحد.</p>
 */
public record AlertRuleDraft(
        @NotNull AlertType type,
        boolean enabled,
        @NotNull AlertAudience audience,
        @NotNull AlertSeverity severity,
        Integer threshold,
        Integer windowDays,
        Integer cooldownDays) {
}
