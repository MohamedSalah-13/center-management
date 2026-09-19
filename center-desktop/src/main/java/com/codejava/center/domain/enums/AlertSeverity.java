package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * درجة إلحاح التنبيه.
 *
 * <p>ثلاث درجات لا خمس: الدرجة التي لا يستطيع المستخدم أن يقرّر عندها فعلاً مختلفاً
 * لا تضيف شيئاً، وقائمة بخمسة ألوان تُقرأ كلها بنفس اللامبالاة. المعنى العملي هنا:
 * {@link #CRITICAL} يعني أن السنتر يخسر شيئاً الآن (لا نسخة احتياطية، حصة مفتوحة تمنع
 * صرف مستحقات)، و{@link #WARNING} يعني أن أحداً يجب أن ينظر اليوم، و{@link #INFO}
 * خبر يُقرأ وقت الفراغ.</p>
 *
 * <p>الترتيب من الأعلى إلى الأدنى إلحاحاً، فيصلح {@code ordinal} لفرز الصندوق مباشرةً
 * دون جدول أولويات منفصل.</p>
 */
public enum AlertSeverity {

    CRITICAL,
    WARNING,
    INFO;

    /** الاسم المعروض بلغة الواجهة - المفتاح {@code alertSeverity.<NAME>} */
    public String getDisplayName() {
        return I18n.get("alertSeverity." + name());
    }

    /**
     * هل هذه الدرجة أشدّ من الحدّ المعطى أو تساويه؟
     *
     * <p>دالةٌ لا مقارنةَ {@code ordinal} مكتوبة في موضع استعمالها، لأن الترتيب هنا
     * مقلوب عمّا يتوقعه القارئ: {@code CRITICAL} هو <b>الأصغر</b> ترتيباً لأنه الأعلى
     * إلحاحاً. مقارنةٌ منثورة في الشاشات تُكتب يوماً بـ {@code >=} فينقلب المعنى تماماً
     * - فيصمت البرنامج عن الحرج ويقفز بالمعلومات - وهو عطبٌ لا يظهر إلا عند العميل.</p>
     */
    public boolean isAtLeast(AlertSeverity minimum) {
        return minimum == null || ordinal() <= minimum.ordinal();
    }

    /** لون خلفية الصف في صندوق التنبيهات؛ الحرج يجب أن يُرى دون قراءة سطره */
    public String getRowColor() {
        return switch (this) {
            case CRITICAL -> "#fdecea";
            case WARNING -> "#fff6e5";
            case INFO -> "";
        };
    }

    /**
     * لون مُشبع للشريط الجانبي في البطاقة المنبثقة وللنقطة في قائمة الجرس.
     *
     * <p>غير {@link #getRowColor()} عن قصد: ذاك خلفية صف يُكتب فوقها نصّ أسود فوجب أن
     * تكون باهتة، وهذا شريط بعرض ستّ نقاط لا نصّ عليه - الباهت فيه لا يُرى أصلاً.</p>
     */
    public String getAccentColor() {
        return switch (this) {
            case CRITICAL -> "#c0392b";
            case WARNING -> "#e67e22";
            case INFO -> "#2980b9";
        };
    }
}
