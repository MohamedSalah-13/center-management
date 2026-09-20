package com.codejava.center.util;

import com.codejava.center.core.money.Money;
import com.codejava.center.domain.enums.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * المبالغ المالية بعملة هذا السنتر.
 *
 * <p>الحساب نفسه - الخانتان والتقريب - في {@link Money} بالنواة، وهذا الصنف يضمّ إليه
 * ما لا تعرفه النواة: عملةَ السنتر المقروءة من إعداداته، ورمزَها المقروء من حزمة نصوص
 * بلغة هذا الجهاز. الدوال تبقى هنا بأسمائها فلا يتغيّر شيء عند الستين موضعاً التي
 * تستدعيها.</p>
 *
 * <h2>العملة</h2>
 *
 * <p>عملة السنتر تُختار من شاشة الإعدادات وتُحفظ في {@code CenterSettings}، و<b>من أين
 * تأتي ليس من شأن هذا الصنف</b>: يسأل {@link CurrencyProvider}، ويركّب سطحُ المكتب
 * جوابه (سنترٌ واحد، قيمةٌ تُقرأ عند الإقلاع وتُحدَّث بعد كل حفظ) بينما يركّب الخادم
 * جوابه (سنترُ هذا الطلب، بذاكرة مؤقتة لكل مؤسسة). تماماً كما يفعل {@link I18n} مع
 * {@code LocaleProvider} ولنفس السبب في السكون: الـ enums وطبقة الخدمات على خيوط
 * ForkJoinPool لا تقبل الحقن.</p>
 *
 * <p>والسؤال يُطرح عند كل مبلغ لا مرةً عند الإقلاع. على جهازٍ واحد لا فرق، وعلى خادمٍ
 * هو الفرق كله: حقلٌ واحد في الـ JVM يجعل آخرَ سنترٍ حفظ إعداداته يذيّل مبالغ السناتر
 * الأخرى برمز عملته. والتنفيذ - لا هذا الصنف - هو من يملك الذاكرة المؤقتة، لأنه وحده
 * يعرف بأيّ مفتاح تُحفظ.</p>
 *
 * <p>الرمز نفسه لا يُحفظ هنا بل يُقرأ من حزمة النصوص عند كل عرض: العملة اختيار السنتر
 * واللغة اختيار الجهاز، فالجنيه المصري يُكتب "ج.م" على تيرمينال عربي و"EGP" على آخر
 * إنجليزي في اللحظة نفسها.</p>
 *
 * <h2>لماذا تبقى الخانات العشرية اثنتين مهما كانت العملة</h2>
 *
 * <p>{@link #SCALE} ليس خيار عرض بل شكل التخزين: كل عمود مالي في المخطط
 * {@code DECIMAL(12,2)}. عملة بثلاث خانات (كالدينار) أو بلا خانات (كالين) تحتاج تغيير
 * كل تلك الأعمدة وترحيلاً يعيد حساب ما فيها، لا سطراً هنا - والعملات الأربع المدعومة
 * كلها بخانتين.</p>
 */
public final class MoneyUtils {

    public static final int SCALE = Money.SCALE;
    public static final RoundingMode ROUNDING = Money.ROUNDING;
    public static final BigDecimal ZERO = Money.ZERO;

    /**
     * الجواب الافتراضي: الجنيه، ما لم تركّب الحافةُ مصدراً.
     *
     * <p>حتى لا يكون على كل اختبارٍ يلمس مبلغاً أن يركّب شيئاً، ولأن {@link Currency#DEFAULT}
     * هي بعينها القيمة التي تعنيها قاعدةٌ لم تُختر فيها عملة.</p>
     */
    private static volatile CurrencyProvider provider = () -> Currency.DEFAULT;

    private MoneyUtils() {
    }

    /**
     * يركّب مصدر العملة. يُستدعى مرة عند إقلاع الحافة.
     *
     * @param source مصدرٌ لا يكون {@code null} هو نفسه - وإن جاز أن يعيد {@code null}
     */
    public static void install(CurrencyProvider source) {
        provider = Objects.requireNonNull(source, "currency provider");
    }

    /** المصدر المركَّب - موجودة ليعيده اختبارٌ بدّله إلى ما كان */
    public static CurrencyProvider provider() {
        return provider;
    }

    /**
     * عملة السنتر الحالي.
     *
     * <p>{@code null} من المصدر يعني عملة غير مضبوطة في القاعدة - قاعدة مُرقّاة أو تركيب
     * جديد - فتُستعمل {@link Currency#DEFAULT}. وهذا هو الموضع الوحيد الذي يُحلّ فيه ذلك:
     * تركُه لكل تنفيذ يعيد الخللَ الذي وقع في مدّة حفظ النسخ، حين قرأت الشاشة "غير مضبوط"
     * على أنه ثلاثون وقرأته الخدمة على أنه "احتفظ بكل شيء".</p>
     */
    public static Currency currency() {
        Currency value = provider.current();
        return value == null ? Currency.DEFAULT : value;
    }

    /** رمز العملة الحالية بلغة الواجهة الحالية */
    public static String currencySymbol() {
        return currency().getSymbol();
    }

    /** ضبط المبلغ على خانتين عشريتين، مع اعتبار null صفراً */
    public static BigDecimal normalize(BigDecimal value) {
        return Money.normalize(value);
    }

    /** تنسيق المبلغ للعرض في الواجهة (بدون صيغة أسّية) */
    public static String format(BigDecimal value) {
        return Money.format(value);
    }

    /**
     * تنسيق المبلغ مع إضافة رمز العملة.
     * الرمز يأتي من عملة السنتر ومن حزمة النصوص، لا مكتوباً هنا، وإلا ظهرت واجهة
     * إنجليزية بمبالغ مذيَّلة برمز عربي - أو سنتر سعودي بمبالغ بالجنيه.
     */
    public static String formatWithCurrency(BigDecimal value) {
        return format(value) + " " + currencySymbol();
    }
}
