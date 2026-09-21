package com.codejava.center.util;

/**
 * الأسماء المعروضة لأنواع عمولة المعلم.
 *
 * <p>نوع العمولة مخزَّن كنص ({@code PERCENTAGE} / {@code FIXED_AMOUNT} / {@code RENT})
 * لا كـ enum، وكان تحويله لاسم عربي مكتوباً مرتين: مرة في شاشة صرف المستحقات ومرة في
 * كشف حساب المعلم. مع الترجمة كان سيصبح موضعين يجب تعديلهما معاً عند إضافة نوع.</p>
 */
public final class CommissionTypes {

    /**
     * الأنواع التي يعرفها حسابُ المستحقات.
     *
     * <p>هي بعينها فروعُ {@code TeacherService.calculatePayout}، ويجب أن تبقيا في خطوة
     * واحدة: نوعٌ هنا بلا فرعٍ هناك يعني معلماً يُحفظ بنجاح ثم يسقط صرفُ كل حصةٍ له -
     * ونوعٌ هناك بلا اسمٍ هنا يُعرض خاماً. {@code TeacherDraftTest} يقف على الأولى.</p>
     */
    public static final java.util.List<String> KNOWN =
            java.util.List.of("PERCENTAGE", "FIXED_AMOUNT", "RENT");

    private CommissionTypes() {
    }

    /**
     * يرفض نوعاً لا يعرفه الحساب، <b>عند الحفظ لا عند الصرف</b>.
     *
     * <p>كان يُقبل أيُّ نصّ، فلا يظهر الخطأ إلا يوم يُصرف لذلك المعلم: رسالةٌ عن نوعِ
     * عمولةٍ مجهول أمام من يعدّ المال، بعد أسابيع من الحفظ ومن شخصٍ آخر غالباً.</p>
     */
    public static void requireKnown(String commissionType) {
        if (!KNOWN.contains(commissionType)) {
            throw new IllegalArgumentException(
                    I18n.format("error.teacher.unknownCommission", commissionType));
        }
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
