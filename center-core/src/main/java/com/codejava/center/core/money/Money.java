package com.codejava.center.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * حساب المبالغ: خانتان عشريتان وتقريب واحد لكل النظام.
 *
 * <p>الغرض أن يكون لكل مبلغ في النظام نفس عدد الخانات ونفس طريقة التقريب، وألا يظهر في
 * الواجهة رقم مثل {@code 99.99000000000001}.</p>
 *
 * <p><b>{@link #SCALE} ليس خيار عرض بل شكل التخزين:</b> كل عمود مالي في المخطط
 * {@code DECIMAL(12,2)}. عملة بثلاث خانات (كالدينار) أو بلا خانات (كالين) تحتاج تغيير كل
 * تلك الأعمدة وترحيلاً يعيد حساب ما فيها، لا سطراً هنا.</p>
 *
 * <p>العملة نفسها ليست هنا: اختيارها قرار سنتر يُقرأ من إعداداته، ورمزها يُقرأ من حزمة
 * نصوص بلغة الجهاز — والنواة لا تعرف قاعدة بيانات ولا لغة. راجع {@code util/MoneyUtils}
 * على جهة التطبيق، وهو من يضمّ الرمز إلى ما يخرج من هنا.</p>
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** ضبط المبلغ على خانتين عشريتين، مع اعتبار {@code null} صفراً */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    /** المبلغ نصّاً بلا صيغة أسّية */
    public static String format(BigDecimal value) {
        return normalize(value).toPlainString();
    }
}
