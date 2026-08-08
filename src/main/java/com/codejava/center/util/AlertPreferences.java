package com.codejava.center.util;

import com.codejava.center.domain.enums.AlertSeverity;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * كيف تظهر التنبيهات على <b>هذا الجهاز</b>.
 *
 * <p>ما يُنبَّه عليه قرار السنتر ({@code alert_rules})، وكيف يُعرض قرار الجهاز - وهو
 * نفس الفصل الذي جعل الطابعة واللغة تفضيلاتِ جهاز بينما موعد النسخ الاحتياطي إعدادَ
 * سنتر. والسبب هنا ملموس: جهاز الاستقبال أمام أولياء الأمور طوال اليوم، وبطاقة تقفز
 * في زاويته تقول "الطالب فلان عليه ٣٠٠ جنيه" تكشف أرقام الناس لمن يقف في الطابور.
 * جهاز المدير في مكتبه لا مشكلة فيه.</p>
 *
 * <p>وككل ما يُحفظ لكل جهاز في هذا المشروع فلا ملف ترحيل Flyway له.</p>
 */
public final class AlertPreferences {

    private static final String POPUP_KEY = "alerts.popupEnabled";
    private static final String TRAY_KEY = "alerts.trayEnabled";
    private static final String MIN_SEVERITY_KEY = "alerts.minimumSeverity";
    private static final String DAY_BRIEFING_KEY = "alerts.dayBriefingEnabled";
    private static final String SOUND_KEY = "alerts.soundEnabled";

    /**
     * أدنى درجة تستحق أن تقفز أمام المستخدم. {@code WARNING} لا {@code INFO}:
     * تأكيد استلام دفعة ورصيدٌ أوشك على النفاد يقعان عشرات المرات في اليوم، وبطاقة
     * لكلٍّ منها تعلّم المستخدم أن يغلق البطاقات دون قراءتها - فيغلق الحرجة معها.
     */
    public static final AlertSeverity DEFAULT_MINIMUM_SEVERITY = AlertSeverity.WARNING;

    private AlertPreferences() {
    }

    /** هل تظهر البطاقة المنبثقة داخل البرنامج على هذا الجهاز؟ */
    public static boolean popupEnabled() {
        return prefs().getBoolean(POPUP_KEY, true);
    }

    /** هل يُرسل إشعار ويندوز من شريط المهام على هذا الجهاز؟ */
    public static boolean trayEnabled() {
        return prefs().getBoolean(TRAY_KEY, true);
    }

    public static AlertSeverity minimumSeverity() {
        String saved = prefs().get(MIN_SEVERITY_KEY, null);
        if (saved == null) {
            return DEFAULT_MINIMUM_SEVERITY;
        }
        try {
            return AlertSeverity.valueOf(saved);
        } catch (IllegalArgumentException e) {
            // قيمة كتبها إصدار أحدث أو عبث يدوي بالسجل: الرجوع للافتراضي أفضل من التعطّل
            return DEFAULT_MINIMUM_SEVERITY;
        }
    }

    /**
     * هل يُعرض موجز جدول اليوم عند فتح البرنامج على هذا الجهاز؟
     *
     * <p>مفتاح مستقلّ عن {@link #popupEnabled()} وعن أدنى درجة، لا تنبيهٌ من الأنواع:
     * الموجز خبرُ بداية اليوم لمن يجلس على المكتب، يظهر مرة واحدة عند الدخول ولا
     * يذكر اسم طالب ولا مبلغاً - فإخفاؤه مع بطاقات الأرصدة يُسكت ما لا سبب لإسكاته،
     * وإظهاره تحت حدّ {@code WARNING} يجعله يختفي عند أول من يرفع الحدّ.</p>
     */
    public static boolean dayBriefingEnabled() {
        return prefs().getBoolean(DAY_BRIEFING_KEY, true);
    }

    /**
     * هل تُرافق البطاقةَ نغمةٌ على هذا الجهاز؟
     *
     * <p>مفتاح جهاز لا سنتر، للسبب نفسه المكتوب أعلاه مقلوباً: مكتب الاستقبال يجلس فيه
     * من عينه على الطالب لا على الشاشة فيحتاجها، ومكتب المدير قد يكون فيه اجتماع.</p>
     */
    public static boolean soundEnabled() {
        return prefs().getBoolean(SOUND_KEY, true);
    }

    /** هل تستحق هذه الدرجة أن تقفز أمام المستخدم على هذا الجهاز؟ */
    public static boolean shouldAnnounce(AlertSeverity severity) {
        return severity != null && severity.isAtLeast(minimumSeverity());
    }

    public static void save(boolean popup, boolean tray, boolean dayBriefing, boolean sound,
                            AlertSeverity minimumSeverity) {
        try {
            Preferences prefs = prefs();
            prefs.putBoolean(POPUP_KEY, popup);
            prefs.putBoolean(TRAY_KEY, tray);
            prefs.putBoolean(DAY_BRIEFING_KEY, dayBriefing);
            prefs.putBoolean(SOUND_KEY, sound);
            prefs.put(MIN_SEVERITY_KEY,
                    (minimumSeverity == null ? DEFAULT_MINIMUM_SEVERITY : minimumSeverity).name());
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.alerts.prefsNotSaved"), e);
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(AlertPreferences.class);
    }
}
