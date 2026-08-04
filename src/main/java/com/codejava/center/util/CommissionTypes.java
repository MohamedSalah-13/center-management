package com.codejava.center.util;

/**
 * الأسماء المعروضة لأنواع عمولة المعلم.
 *
 * <p>نوع العمولة مخزَّن كنص ({@code PERCENTAGE} / {@code FIXED_AMOUNT} / {@code RENT})
 * لا كـ enum، وكان تحويله لاسم عربي مكتوباً مرتين: مرة في شاشة صرف المستحقات ومرة في
 * كشف حساب المعلم. مع الترجمة كان سيصبح موضعين يجب تعديلهما معاً عند إضافة نوع.</p>
 */
public final class CommissionTypes {

    private CommissionTypes() {
    }

    /** الاسم المعروض بلغة الواجهة، والنوع نفسه إن كان غير معروف */
    public static String displayName(String commissionType) {
        if (commissionType == null) {
            return I18n.get("common.none");
        }
        String label = I18n.get("commission." + commissionType);
        // I18n.get يُعيد !key! للمفتاح المفقود؛ النوع الخام أوضح للمستخدم من ذلك
        return label.startsWith("!") ? commissionType : label;
    }
}
