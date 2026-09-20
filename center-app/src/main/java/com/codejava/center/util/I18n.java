package com.codejava.center.util;

import com.codejava.center.core.i18n.LocaleProvider;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * لغة الواجهة: مصدر واحد للحزمة النصية والـ Locale الحالي.
 *
 * <p>ثابت (static) لا bean من Spring عن قصد: الـ enums (مثل {@link com.codejava.center.domain.enums.Role})
 * تحتاج اسماً مترجَماً وهي لا تقبل الحقن، وطبقة الخدمات تُنفَّذ على خيوط ForkJoinPool
 * حيث لا يُتاح سياق مرتبط بالخيط. نفس أسلوب {@code FxAsync} و{@link MoneyUtils}.</p>
 *
 * <p><b>من أين تأتي اللغة ليس من شأن هذا الصنف.</b> يسأل {@link LocaleProvider} في النواة،
 * ويركّب سطحُ المكتب جوابه (تفضيلُ الجهاز في سجلّ ويندوز) بينما يركّب الخادم جوابه
 * (ترويسة الطلب أو تفضيل صاحب الحساب). ولهذا لم يعد هنا سطرٌ واحد يقرأ
 * {@code java.util.prefs}: سجلّ ويندوز مصدرٌ لا يملكه خادم، والمئتان والثمانون استدعاءً
 * لـ {@code get} و{@code format} لم يتغيّر منها شيء.</p>
 *
 * <p>ولا يضبط هذا الصنف {@link Locale#setDefault} كذلك. ضبط الافتراضي أثرٌ على مستوى
 * الـ JVM كاملاً، وعلى خادمٍ يخدم عشرة سناتر بعشر لغات يعني أن آخر طلبٍ وصل يقرّر بأيّ
 * لغة تُكتب أسماءُ شهور الطلبات الأخرى. من يملك الـ JVM وحده يملك ذلك القرار، وهو
 * تطبيق سطح المكتب: {@code LanguagePreferences} هناك يضبطه لأن {@code DatePicker} وأزرار
 * JavaFX الداخلية لا تقرأ حزمتنا أصلاً.</p>
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

    /**
     * حزمةٌ محمَّلة لكل لغة، لا حزمةٌ واحدة "حالية".
     *
     * <p>الحقل الواحد كان يصحّ ما دامت اللغة خاصيةَ البرنامج كله: تتبدّل مرة فتُعاد
     * قراءته. وخادمٌ يجيب طلبين بلغتين في اللحظة نفسها يجعل "الحزمة الحالية" جملةً بلا
     * معنى - ومن يكتبها يكتب عبارةً عربية في صفحة إنجليزية بلا خطأ في أي سجل.</p>
     *
     * <p>وهي ذاكرةٌ مؤقتة لا ترفٌ: {@code get} تُستدعى مرة لكل خانة في جدول ولكل سطر في
     * كشف، و{@code getBundle} تبحث في مسار الأصناف.</p>
     */
    private static final Map<Locale, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();

    /**
     * الجواب الافتراضي: العربية، ما لم تركّب الحافةُ مصدراً.
     *
     * <p>حتى لا يكون على كل اختبارٍ يلمس نصاً أن يركّب شيئاً، وحتى تبقى لغةُ سنترٍ مصري
     * هي ما يظهر إن نُسي التركيب في وحدةٍ جديدة - لا لغةُ الخادم الذي يستضيفها.</p>
     */
    private static volatile LocaleProvider provider = () -> ARABIC;

    private I18n() {
    }

    /**
     * يركّب مصدر اللغة. يُستدعى مرة عند إقلاع الحافة قبل رسم أول شاشة أو خدمة أول طلب.
     *
     * @param source مصدرٌ لا يعيد {@code null}
     */
    public static void install(LocaleProvider source) {
        provider = Objects.requireNonNull(source, "locale provider");
    }

    /** المصدر المركَّب - موجودة ليعيده اختبارٌ بدّله إلى ما كان */
    public static LocaleProvider provider() {
        return provider;
    }

    /** اللغات التي تدعمها الواجهة، بالترتيب الذي تظهر به في قائمة الاختيار */
    public static Locale[] supportedLocales() {
        return new Locale[]{ARABIC, ENGLISH};
    }

    public static Locale current() {
        return provider.current();
    }

    public static ResourceBundle bundle() {
        return bundleFor(current());
    }

    /** هل اللغة الحالية تُكتب من اليمين لليسار (يحدد اتجاه الواجهة) */
    public static boolean isRightToLeft() {
        return "ar".equals(current().getLanguage());
    }

    /**
     * نص المفتاح باللغة الحالية.
     * المفتاح المفقود يُعاد كما هو بين علامتَي تعجب بدل رمي استثناء: نص غريب في زر
     * أهون على المستخدم من شاشة لا تُفتح، ويظهر فوراً للمطوّر أثناء الاختبار.
     */
    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /** نص المفتاح مع تعويض الوسائط بصيغة {0}، {1} */
    public static String format(String key, Object... args) {
        Locale locale = current();
        return new MessageFormat(text(locale, key), locale).format(args);
    }

    /** اسم اللغة كما يُعرض في قائمة الاختيار (كل لغة باسمها هي، لا مترجَمة) */
    public static String displayName(Locale locale) {
        return "ar".equals(locale.getLanguage()) ? "العربية" : "English";
    }

    private static String text(Locale locale, String key) {
        try {
            return bundleFor(locale).getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /**
     * تُفرّغ الذاكرة المؤقتة للحزم. للاختبار وحده - ولهذا ليست عامة.
     *
     * <p>حارسُ {@link #NO_SYSTEM_LOCALE_FALLBACK} لا يمكن التحقق منه إلا على تحميلٍ
     * بارد: حزمةٌ حُمّلت مرة تُعاد من الخريطة مهما صار افتراضيُّ الـ JVM بعدها، فاختبارٌ
     * يضبط الافتراضي ثم يسأل عن العربية يمرّ حتى لو حُذف الحارس.</p>
     */
    static void clearBundleCache() {
        BUNDLES.clear();
    }

    private static ResourceBundle bundleFor(Locale locale) {
        return BUNDLES.computeIfAbsent(locale, requested -> ResourceBundle.getBundle(
                BUNDLE_NAME, requested, I18n.class.getClassLoader(), NO_SYSTEM_LOCALE_FALLBACK));
    }
}
