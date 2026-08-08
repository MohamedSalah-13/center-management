package com.codejava.center.util;

import com.codejava.center.domain.enums.Role;
import javafx.scene.input.KeyCode;

/**
 * الأوامر التي يجوز أن يُربط بها اختصار لوحة مفاتيح.
 *
 * <p><b>قائمة معلَنة لا نصّ حرّ.</b> لو كان ما يُحفظ اسمَ شاشة مكتوباً لصار كل حذفٍ أو
 * إعادة تسمية لملف {@code .fxml} اختصاراً يضغطه الموظف فلا يحدث شيء - بلا رسالة ولا أثر.
 * وبكونها enum يصير كل أمر جديد سطراً واحداً هنا، ويفشل البناء إن نُسي اسمه المعروض
 * ({@code MessageBundleTest})، ويستحيل أن يُحفظ اختصارٌ لأمر لا وجود له.</p>
 *
 * <p><b>الافتراضيات قليلة عن قصد.</b> برنامجٌ يحجز عشرين تركيبة {@code Ctrl} أول ما
 * يُثبَّت يأخذها من عادات المستخدم في كل ما سواه، والغالب أن يُطفأ الأمر كلّه. المربوط
 * هنا ما يُفتح كل يوم عشرات المرات، وما عداه يتركه البرنامج فارغاً حتى يطلبه صاحبه.</p>
 *
 * <p>ولا تركيبة منها تمسّ ما يفهمه حقل النصّ نفسه: {@code Ctrl+A} و{@code Ctrl+C}
 * و{@code Ctrl+V} و{@code Ctrl+Z} تُستهلَك داخل الحقل قبل أن تصل إلى المشهد، فاختصارٌ
 * عليها يعمل في نصف الشاشات ولا يعمل في نصفها - وهو أسوأ من اختصار لا يعمل أبداً.</p>
 */
public enum ShortcutAction {

    HOME(Role.SECRETARY, Shortcut.control(KeyCode.D)),
    STUDENTS(Role.SECRETARY, Shortcut.control(KeyCode.T)),
    ATTENDANCE(Role.SECRETARY, Shortcut.control(KeyCode.H)),
    SESSIONS(Role.SECRETARY, Shortcut.control(KeyCode.E)),
    DAY_SCHEDULE(Role.SECRETARY, Shortcut.control(KeyCode.J)),
    ATTENDANCE_REPORT(Role.SECRETARY, null),
    ATTENDANCE_LOG(Role.SECRETARY, null),
    SHORTCUTS(Role.SECRETARY, null),

    CASHIER(Role.ADMIN, Shortcut.control(KeyCode.K)),
    PAYMENT_HISTORY(Role.ADMIN, null),
    EXPENSES(Role.ADMIN, null),
    SHIFT_CLOSING(Role.ADMIN, null),
    TEACHER_PAYOUT(Role.ADMIN, null),
    ARREARS(Role.ADMIN, null),
    NOTIFICATIONS(Role.ADMIN, null),
    ALERTS(Role.ADMIN, null),
    TEACHERS(Role.ADMIN, null),
    GROUPS(Role.ADMIN, null),
    USERS(Role.ADMIN, null),
    AUDIT(Role.ADMIN, null),
    SETTINGS(Role.ADMIN, Shortcut.control(KeyCode.S)),

    /**
     * نسخة احتياطية فوريّة بلا المرور بشاشة الإعدادات.
     *
     * <p>الأمر الوحيد هنا الذي <b>يفعل</b> شيئاً بدل أن يفتح شاشة، ولهذا هو وحده الذي
     * يسأل قبل أن ينفّذ: شاشةٌ تُفتح بالخطأ تُغلق، ونسخةٌ تبدأ بالخطأ تشغل القرص وتكتب
     * ملفاً باسم لا ينتظره أحد.</p>
     */
    BACKUP_NOW(Role.ADMIN, Shortcut.control(KeyCode.B));

    /**
     * أقلّ صلاحية تكفي لهذا الأمر. {@code SECRETARY} تعني "لكل الأدوار".
     *
     * <p>الفلترة هنا ليست إخفاءً تجميلياً: الاختصارات <b>تُحفظ لكل جهاز</b>، وجهاز
     * الاستقبال يجلس عليه المدير صباحاً والسكرتير بعد الظهر بنفس الإعدادات. فلولا هذا
     * الحقل لكان {@code Ctrl+B} الذي ضبطه المدير يطلق نسخةً احتياطية بيد من لا يملكها -
     * فترفضها طبقة الخدمات وتُكتب محاولةُ رفضٍ في سجل المراقبة لا ذنب لصاحبها فيها.</p>
     */
    private final Role minimumRole;

    /** التركيبة المرافقة للبرنامج، أو {@code null} لأمرٍ يبدأ بلا اختصار */
    private final Shortcut defaultShortcut;

    ShortcutAction(Role minimumRole, Shortcut defaultShortcut) {
        this.minimumRole = minimumRole;
        this.defaultShortcut = defaultShortcut;
    }

    public Shortcut getDefaultShortcut() {
        return defaultShortcut;
    }

    /** الاسم المعروض بلغة الواجهة - {@code shortcutAction.HOME} في حزمة النصوص */
    public String getDisplayName() {
        return I18n.get("shortcutAction." + name());
    }

    public boolean isAllowedFor(Role role) {
        return role != null && (minimumRole == Role.SECRETARY || role == Role.ADMIN);
    }
}
