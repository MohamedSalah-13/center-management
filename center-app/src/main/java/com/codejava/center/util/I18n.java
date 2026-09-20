package com.codejava.center.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * لغة الواجهة: مصدر واحد للحزمة النصية والـ Locale الحالي.
 *
 * <p>ثابت (static) لا bean من Spring عن قصد: الـ enums (مثل {@link com.codejava.center.domain.enums.Role})
 * تحتاج اسماً مترجَماً وهي لا تقبل الحقن، وطبقة الخدمات تُنفَّذ على خيوط ForkJoinPool
 * حيث لا يُتاح سياق مرتبط بالخيط. نفس أسلوب {@link FxAsync} و{@link MoneyUtils}.</p>
 *
 * <p><b>اللغة تُحفظ لكل جهاز</b> عبر {@link Preferences} لا في قاعدة البيانات: شاشة الدخول
 * تحتاج اللغة قبل أن يكون هناك مستخدم أو جلسة، وكل تيرمينال في السنتر قد يخدم مشغّلاً
 * مختلفاً. لهذا لا يوجد ملف ترحيل Flyway مقابل لهذه الميزة.</p>
 *
 * <p>حزمة الأساس {@code messages.properties} عربية، و{@code messages_en.properties} إنجليزية.
 * الترتيب مقصود: أي مفتاح ينساه المطوّر في الملف الإنجليزي يظهر بالعربية بدل أن يُسقِط
 * الشاشة بـ MissingResourceException عند العميل.</p>
 */
public final class I18n {

    /** الأرقام اللاتينية مفروضة على العربية: CLDR يختار الأرقام الهندية لـ ar-EG وهي غير مألوفة في إيصالات السنتر */
    public static final Locale ARABIC = Locale.forLanguageTag("ar-EG-u-nu-latn");
    public static final Locale ENGLISH = Locale.forLanguageTag("en");

    private static final String BUNDLE_NAME = "i18n.messages";
    private static final String PREF_KEY = "ui.language";

    /**
     * يمنع السقوط إلى لغة نظام التشغيل.
     *
     * <p>سلوك {@link ResourceBundle} الافتراضي عند عدم وجود ملف للّغة المطلوبة هو تجريب
     * <b>لغة الـ JVM الافتراضية قبل حزمة الأساس</b>. وبما أن العربية هي حزمة الأساس بلا
     * لاحقة، كان طلب العربية على ويندوز إنجليزي يمرّ على {@code messages_en} فيجدها
     * ويعيدها: برنامج مضبوط على العربية يعرض إنجليزية كاملة.</p>
     *
     * <p>بإرجاع null يصبح الترتيب: الملف المطلوب ثم حزمة الأساس (العربية) مباشرةً.</p>
     */
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    };

    private static volatile Locale currentLocale;
    private static volatile ResourceBundle currentBundle;

    static {
        apply(readPersistedLocale());
    }

    private I18n() {
    }

    /** اللغات التي تدعمها الواجهة، بالترتيب الذي تظهر به في قائمة الاختيار */
    public static Locale[] supportedLocales() {
        return new Locale[]{ARABIC, ENGLISH};
    }

    public static Locale current() {
        return currentLocale;
    }

    public static ResourceBundle bundle() {
        return currentBundle;
    }

    /** هل اللغة الحالية تُكتب من اليمين لليسار (يحدد اتجاه الواجهة) */
    public static boolean isRightToLeft() {
        return "ar".equals(currentLocale.getLanguage());
    }

    /**
     * نص المفتاح باللغة الحالية.
     * المفتاح المفقود يُعاد كما هو بين علامتَي تعجب بدل رمي استثناء: نص غريب في زر
     * أهون على المستخدم من شاشة لا تُفتح، ويظهر فوراً للمطوّر أثناء الاختبار.
     */
    public static String get(String key) {
        try {
            return currentBundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /** نص المفتاح مع تعويض الوسائط بصيغة {0}، {1} */
    public static String format(String key, Object... args) {
        return new MessageFormat(get(key), currentLocale).format(args);
    }

    /** اسم اللغة كما يُعرض في قائمة الاختيار (كل لغة باسمها هي، لا مترجَمة) */
    public static String displayName(Locale locale) {
        return "ar".equals(locale.getLanguage()) ? "العربية" : "English";
    }

    /**
     * تبديل لغة الواجهة: يحدّث الحزمة، ويحفظ الاختيار، ويضبط Locale الافتراضي للـ JVM.
     *
     * <p>ضبط الافتراضي ضروري لا تجميلي: أسماء الشهور في {@code DatePicker}، ونصوص أزرار
     * {@code ButtonType} (موافق/إلغاء)، وقائمة {@code TableView} السياقية كلها تأتي من
     * حزم JavaFX الداخلية التي تقرأ {@link Locale#getDefault()} ولا تعرف شيئاً عن حزمتنا.</p>
     *
     * <p>لا تُعيد هذه الدالة بناء الشاشة المعروضة؛ ذلك من مسؤولية
     * {@code ViewLoader.reloadScene} لأنه وحده يملك الـ Stage.</p>
     */
    public static void setLocale(Locale locale) {
        apply(locale);
        installAsJvmDefault();
        persist(locale);
    }

    /**
     * يجعل لغة الواجهة هي Locale الافتراضي للـ JVM.
     *
     * <p>يُستدعى من {@code JavaFxApplication.init()} لا من المُهيّئ الساكن: تغيير الافتراضي
     * أثر جانبي على مستوى الـ JVM كامل، ولا يصح أن يقع لمجرد أن صنفاً ما لمس {@code I18n}
     * أثناء اختبار وحدة.</p>
     */
    public static void installAsJvmDefault() {
        Locale.setDefault(currentLocale);
    }

    private static void apply(Locale locale) {
        currentLocale = locale;
        // clearCache وإلا أعادت getBundle الحزمة القديمة من ذاكرة ResourceBundle عند التبديل
        ResourceBundle.clearCache(I18n.class.getClassLoader());
        currentBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale,
                I18n.class.getClassLoader(), NO_SYSTEM_LOCALE_FALLBACK);
    }

    private static Locale readPersistedLocale() {
        try {
            String tag = prefs().get(PREF_KEY, null);
            if (tag != null && "en".equals(Locale.forLanguageTag(tag).getLanguage())) {
                return ENGLISH;
            }
        } catch (SecurityException e) {
            // بعض بيئات ويندوز المقيَّدة تمنع قراءة سجل المستخدم؛ العربية هي الافتراضي على أي حال
        }
        return ARABIC;
    }

    private static void persist(Locale locale) {
        try {
            Preferences prefs = prefs();
            prefs.put(PREF_KEY, locale.getLanguage());
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // فشل الحفظ يعني أن اللغة تعود للعربية بعد إعادة التشغيل فقط، لا يستحق إسقاط العملية
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(I18n.class);
    }
}
