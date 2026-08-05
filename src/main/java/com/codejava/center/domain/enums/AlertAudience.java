package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * وجهة التنبيه: من يصله.
 *
 * <p>محور مستقل عن {@link AlertCategory}، وهو ما يجعل سجلاً واحداً من الأنواع يكفي
 * للصندوق الداخلي ولرسائل أولياء الأمور معاً. النوع نفسه ({@code ARREARS}) يمكن أن
 * يُعرض للمدير وحده اليوم، ثم يُرسل لولي الأمر أيضاً غداً، بتغيير قيمة واحدة في
 * الشاشة لا بكتابة نوع ثانٍ.</p>
 *
 * <p><b>ولا يُفعَّل الإرسال لأولياء الأمور تلقائياً أبداً بترقية.</b> كل قاعدة تصل
 * وجهتها الافتراضية {@link #INTERNAL}؛ الانتقال إلى {@link #PARENTS} أو {@link #BOTH}
 * قرار يتخذه صاحب السنتر صراحةً، لأن معناه أن البرنامج صار يتكلم باسمه بلا ضغطة من
 * موظف - وأول ما يقوله رسائل عن مال.</p>
 */
public enum AlertAudience {

    /** صندوق التنبيهات داخل البرنامج وحده */
    INTERNAL,

    /** رسالة إلى ولي الأمر وحدها، بلا سطر في الصندوق */
    PARENTS,

    /** الاثنان معاً */
    BOTH;

    public boolean includesInternal() {
        return this == INTERNAL || this == BOTH;
    }

    public boolean includesParents() {
        return this == PARENTS || this == BOTH;
    }

    /** الاسم المعروض بلغة الواجهة - المفتاح {@code alertAudience.<NAME>} */
    public String getDisplayName() {
        return I18n.get("alertAudience." + name());
    }
}
