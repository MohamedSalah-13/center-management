package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;

import java.util.Arrays;
import java.util.List;

/**
 * سجل أنواع التنبيهات: مفردات النظام كلها في مكان واحد.
 *
 * <p>هذا الـ enum هو ما حلّ محلّ {@code NotificationType} القديم. قيمتاه الأوليان
 * ({@code ABSENCE} و{@code ARREARS}) بقيتا باسميهما بالضبط، فصفوف {@code notification_logs}
 * المكتوبة قبل هذا التعديل تبقى مقروءة بلا ترحيل بيانات - لم يتغيّر إلا اتساع العمود.</p>
 *
 * <h2>محوران لا محور واحد</h2>
 *
 * <p>لكل نوع {@link AlertCategory} (موضوعه) و{@link AlertAudience} (وجهته)، وهما مستقلان.
 * بذلك يكفي سجل واحد من الأنواع للصندوق الداخلي ولرسائل أولياء الأمور معاً: تحويل
 * {@code ARREARS} من "للمدير" إلى "للمدير ولولي الأمر" تغييرُ قيمة في الشاشة، لا نوع
 * ثانٍ يُكتب في الكود ويُنسى تحديثه مع الأول.</p>
 *
 * <h2>الوجهة الافتراضية داخلية دائماً</h2>
 *
 * <p>ولو كان النوع موجَّهاً بطبعه إلى ولي الأمر. ترقية البرنامج يجب ألا تجعله يبدأ
 * مراسلة أولياء الأمور - عن مال غالباً - دون أن يطلب أحد ذلك. {@link #parentCapable}
 * يقول إن النوع <b>يصلح</b> للإرسال، وقرار الإرسال يبقى لصاحب السنتر في شاشة الإدارة.</p>
 *
 * <h2>معاملان لا صيغة حرة</h2>
 *
 * <p>{@code threshold} و{@code windowDays} يغطّيان كل ما تحتاجه القواعد القائمة: "ثلاث
 * غيابات خلال ثلاثين يوماً"، "متأخرات تتجاوز خمسين جنيهاً"، "لم يحضر منذ أربعة عشر
 * يوماً". معناهما يختلف باختلاف النوع، ولذلك يُشرح كلٌّ منهما في الشاشة بمفتاح خاص به
 * ({@code alertType.<NAME>.threshold}) بدل تسمية عامة لا تقول للمستخدم ماذا يكتب.</p>
 *
 * <p>البديل - عمود JSON بمعاملات حرة - يبدو أوسع وهو أضيق عملياً: لا تستطيع الشاشة أن
 * تعرض حقولاً لما لا تعرف شكله، فينتهي إلى مربّع نصّ يكتب فيه صاحب السنتر تهيئةً بلا
 * تحقّق. حين يحتاج نوع جديد معاملاً ثالثاً يُضاف عمود ثالث، وهو ترحيل واحد.</p>
 */
public enum AlertType {

    // ------------------------------------------------------------------- الطلاب
    /** تغيّب الطالب عن عدد من الحصص خلال الفترة */
    ABSENCE(AlertCategory.STUDENTS, AlertSeverity.WARNING, true, 3, 30, 7),

    /** طالب نشط لم يُسجَّل له حضور منذ مدة - انقطاع لم يُعلَن */
    STUDENT_INACTIVE(AlertCategory.STUDENTS, AlertSeverity.WARNING, false, null, 14, 14),

    // ------------------------------------------------------------------ المالية
    /** رصيد الطالب سالب: عليه مستحقات */
    ARREARS(AlertCategory.FINANCE, AlertSeverity.WARNING, true, 1, null, 7),

    /** الرصيد ما زال موجباً لكنه أوشك على النفاد؛ التنبيه قبل أن يقف الطالب على البوابة */
    LOW_BALANCE(AlertCategory.FINANCE, AlertSeverity.INFO, true, 50, null, 14),

    /** تأكيد استلام دفعة - يُطلق لحظة التحصيل لا في الفحص المجدول */
    PAYMENT_RECEIPT(AlertCategory.FINANCE, AlertSeverity.INFO, true, null, null, 0),

    /** حصص مغلقة لم تُصرَف مستحقات معلمها بعد */
    TEACHER_DUES_PENDING(AlertCategory.FINANCE, AlertSeverity.WARNING, false, 5, 14, 7),

    // ----------------------------------------------------------------- التشغيل
    /** حصة بقيت مفتوحة بعد يومها: تمنع فتح حصة جديدة للمجموعة وتؤجّل صرف مستحقات معلمها */
    SESSION_LEFT_OPEN(AlertCategory.OPERATIONS, AlertSeverity.CRITICAL, false, null, 1, 1),

    /** مجموعة قائمة لم تُعقد لها حصة منذ مدة */
    GROUP_WITHOUT_SESSION(AlertCategory.OPERATIONS, AlertSeverity.INFO, false, null, 14, 14),

    // ------------------------------------------------------------------- النظام
    /** فشلت نسخة احتياطية - يُطلق من المجدوِل لحظة الفشل */
    BACKUP_FAILED(AlertCategory.SYSTEM, AlertSeverity.CRITICAL, false, null, null, 0),

    /** مضى على آخر نسخة ناجحة أكثر مما ينبغي: الفشل ليلةً بعد ليلة لا يُكتشف إلا بهذا */
    BACKUP_OVERDUE(AlertCategory.SYSTEM, AlertSeverity.CRITICAL, false, null, 2, 1),

    /** محاولات دخول فاشلة متكررة */
    FAILED_LOGIN_BURST(AlertCategory.SYSTEM, AlertSeverity.CRITICAL, false, 5, 1, 1),

    /** قناة إرسال الرسائل غير جاهزة بينما هناك قاعدة تعتمد عليها */
    MESSAGING_CHANNEL_DOWN(AlertCategory.SYSTEM, AlertSeverity.WARNING, false, null, null, 1);

    private final AlertCategory category;
    private final AlertSeverity defaultSeverity;
    private final boolean parentCapable;
    private final Integer defaultThreshold;
    private final Integer defaultWindowDays;
    private final int defaultCooldownDays;

    AlertType(AlertCategory category, AlertSeverity defaultSeverity, boolean parentCapable,
              Integer defaultThreshold, Integer defaultWindowDays, int defaultCooldownDays) {
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.parentCapable = parentCapable;
        this.defaultThreshold = defaultThreshold;
        this.defaultWindowDays = defaultWindowDays;
        this.defaultCooldownDays = defaultCooldownDays;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public AlertSeverity getDefaultSeverity() {
        return defaultSeverity;
    }

    /** هل يصحّ أصلاً أن يُرسل هذا النوع إلى ولي أمر؟ فشل نسخة احتياطية لا يُرسل لأحد خارج السنتر */
    public boolean isParentCapable() {
        return parentCapable;
    }

    public Integer getDefaultThreshold() {
        return defaultThreshold;
    }

    public Integer getDefaultWindowDays() {
        return defaultWindowDays;
    }

    public int getDefaultCooldownDays() {
        return defaultCooldownDays;
    }

    public boolean usesThreshold() {
        return defaultThreshold != null;
    }

    public boolean usesWindow() {
        return defaultWindowDays != null;
    }

    /**
     * الوجهة الافتراضية: داخلية دائماً - راجع شرح الصف.
     * دالة لا حقل، حتى لا يظنّ من يقرأ الجدول أن القيمة قابلة للاختلاف بين نوع وآخر.
     */
    public AlertAudience getDefaultAudience() {
        return AlertAudience.INTERNAL;
    }

    /** الاسم المعروض بلغة الواجهة - المفتاح {@code alertType.<NAME>} */
    public String getDisplayName() {
        return I18n.get("alertType." + name());
    }

    /** شرح ما يفعله هذا التنبيه، لشاشة إدارة القواعد */
    public String getDescription() {
        return I18n.get("alertType." + name() + ".desc");
    }

    /**
     * معنى {@code threshold} لهذا النوع تحديداً؛ فارغ إن كان لا يستعمله.
     *
     * <p>رمز العملة وسيط لا نصّ في القالب: حدود المتأخرات والرصيد مبالغ، و"بالجنيه"
     * مكتوبةً في الحزمة تجعل مديراً يضبط حدّاً بعملة غير عملة سنتره. الأنواع التي حدّها
     * عدد (محاولات دخول، أيام) تتجاهل الوسيط.</p>
     */
    public String getThresholdLabel() {
        return usesThreshold()
                ? I18n.format("alertType." + name() + ".threshold", MoneyUtils.currencySymbol())
                : "";
    }

    /** معنى {@code windowDays} لهذا النوع تحديداً؛ فارغ إن كان لا يستعمله */
    public String getWindowLabel() {
        return usesWindow() ? I18n.get("alertType." + name() + ".window") : "";
    }

    /** أنواع تصنيف واحد، لتصفية شاشة الإدارة */
    public static List<AlertType> of(AlertCategory category) {
        return Arrays.stream(values()).filter(type -> type.category == category).toList();
    }
}
