package com.codejava.center.util;

import com.codejava.center.service.notification.WhatsAppLinkStyle;

import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * ما يخصّ <b>هذا الجهاز</b> من إعدادات الإشعارات: شكل رابط واتساب، ومفتاح دخول المزوّد.
 *
 * <p>بقية الإعدادات — القناة، عنوان المزوّد، معرّف المرسل، قالب الرسالة — في
 * {@code center_settings} لأنها قرار السنتر. الاثنان هنا ليسا كذلك:</p>
 *
 * <ul>
 *   <li><b>شكل الرابط</b> يتبع ما هو مثبَّت على الجهاز: {@code whatsapp://} يفتح تطبيق
 *       واتساب مباشرةً على جهاز مثبَّت عليه، ويفشل على جهاز آخر. مثل الطابعة تماماً.</li>
 *   <li><b>مفتاح الدخول</b> سرّ. حفظه في قاعدة البيانات يعني خروجه داخل كل نسخة
 *       احتياطية، بجوار أرقام أولياء الأمور نفسها التي يصلح المفتاح لمراسلتهم بها —
 *       فتصبح فلاشة ضائعة قدرةً على انتحال صفة السنتر. نفس سبب
 *       {@link BackupPreferences}: السرّ لا يُحفظ داخل ما يحميه.</li>
 * </ul>
 *
 * <p>الثمن أن مفتاح المزوّد يُدخَل على كل جهاز يُرسل منه. شاشة الإشعارات مقصورة على
 * المدير أصلاً، فالأجهزة قليلة، والشاشة تقول صراحةً إن الحقل يخصّ هذا الجهاز وحده.</p>
 *
 * <p>وككل ما يُحفظ لكل جهاز في هذا المشروع (اللغة، الطابعة) فلا ملف ترحيل Flyway له.</p>
 */
public final class NotificationPreferences {

    private static final String LINK_STYLE_KEY = "notify.linkStyle";
    private static final String LINK_TEMPLATE_KEY = "notify.linkTemplate";
    private static final String TOKEN_KEY = "notify.apiToken";

    /**
     * ثابت يخصّ هذا السرّ وحده. <b>تغييره يُبطل كل مفتاح محفوظ على أجهزة العملاء</b>،
     * فيتوقف الإرسال عبر المزوّد حتى يُدخَل المفتاح من جديد.
     */
    private static final String OBFUSCATION_SEED = "center-management/notification-token/v1";

    private static final MachineSecret SECRET = MachineSecret.forPurpose(OBFUSCATION_SEED);

    private NotificationPreferences() {
    }

    /** نمط الرابط المضبوط على هذا الجهاز؛ {@code wa.me} لجهاز لم يُضبط بعد كما كان قبل الإعداد */
    public static WhatsAppLinkStyle linkStyle() {
        String saved = read(LINK_STYLE_KEY);
        if (saved == null) {
            return WhatsAppLinkStyle.WA_ME;
        }
        try {
            return WhatsAppLinkStyle.valueOf(saved);
        } catch (IllegalArgumentException e) {
            // قيمة كتبها إصدار أحدث أو عبث يدوي بالسجل: الرجوع للافتراضي أفضل من التعطّل
            return WhatsAppLinkStyle.WA_ME;
        }
    }

    /** قالب الرابط المكتوب يدوياً، أو {@code null} إن لم يُضبط */
    public static String linkTemplate() {
        return read(LINK_TEMPLATE_KEY);
    }

    public static void setLink(WhatsAppLinkStyle style, String customTemplate) {
        try {
            Preferences prefs = prefs();
            prefs.put(LINK_STYLE_KEY, (style == null ? WhatsAppLinkStyle.WA_ME : style).name());
            if (customTemplate == null || customTemplate.isBlank()) {
                prefs.remove(LINK_TEMPLATE_KEY);
            } else {
                prefs.put(LINK_TEMPLATE_KEY, customTemplate.trim());
            }
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.notification.prefsNotSaved"), e);
        }
    }

    /**
     * مفتاح دخول المزوّد، أو {@code null} إن لم يُضبط على هذا الجهاز.
     *
     * <p>يعود {@code char[]} لا {@code String} للسبب نفسه في {@link BackupPreferences}:
     * النص الثابت يبقى في الذاكرة حتى يجمعه الكانس، والمصفوفة تُمحى فور استعمالها.</p>
     */
    public static char[] apiToken() {
        String stored = read(TOKEN_KEY);
        return stored == null ? null : SECRET.reveal(stored);
    }

    /** هل ضُبط مفتاح على هذا الجهاز — للعرض في الشاشة دون كشف المفتاح */
    public static boolean hasApiToken() {
        return read(TOKEN_KEY) != null;
    }

    /**
     * @param token يُمحى محتواها بعد الحفظ؛ {@code null} أو فارغة تعني حذف المفتاح المحفوظ
     */
    public static void setApiToken(char[] token) {
        try {
            Preferences prefs = prefs();
            if (token == null || token.length == 0) {
                prefs.remove(TOKEN_KEY);
            } else {
                prefs.put(TOKEN_KEY, SECRET.conceal(token));
            }
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.notification.prefsNotSaved"), e);
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
        }
    }

    private static String read(String key) {
        try {
            String saved = prefs().get(key, null);
            return saved == null || saved.isBlank() ? null : saved;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(NotificationPreferences.class);
    }
}
