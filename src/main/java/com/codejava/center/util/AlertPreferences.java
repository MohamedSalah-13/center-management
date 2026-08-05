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

    /** هل تستحق هذه الدرجة أن تقفز أمام المستخدم على هذا الجهاز؟ */
    public static boolean shouldAnnounce(AlertSeverity severity) {
        return severity != null && severity.isAtLeast(minimumSeverity());
    }

    public static void save(boolean popup, boolean tray, AlertSeverity minimumSeverity) {
        try {
            Preferences prefs = prefs();
            prefs.putBoolean(POPUP_KEY, popup);
            prefs.putBoolean(TRAY_KEY, tray);
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
