package com.codejava.center.service.alert;

/**
 * كُتب تنبيه جديد في الصندوق على <b>هذا الجهاز</b>.
 *
 * <p>لا يحمل التنبيه نفسه بل إشارةً فقط: "استيقظ واقرأ". {@link AlertFeed} هو الذي
 * يقرأ ما استُجدّ من قاعدة البيانات ويحدّد ما لم يُعرَض بعد. لو حمل الحدث الصفَّ
 * جاهزاً لَصار للعرض مساران - واحد فوريّ وواحد دوريّ - ولَاحتاج كلٌّ منهما حاجزَه
 * ضدّ التكرار، فيظهر التنبيه مرتين حين يتسابقان.</p>
 *
 * <p>ولذلك أيضاً لا يُنشر إلا بعد نجاح الكتابة فعلاً: {@code AlertWriter} يودع في
 * معاملة مستقلة، فما إن يرجع {@code true} حتى يكون الصف مقروءاً من أي خيط.</p>
 */
public record AlertRaisedEvent() {

    private static final AlertRaisedEvent INSTANCE = new AlertRaisedEvent();

    /** لا حالة له، فنسخةٌ واحدة تكفي - وإنشاء كائن لكل تنبيه في دفعة من مئتين هدر */
    public static AlertRaisedEvent instance() {
        return INSTANCE;
    }
}
