package com.codejava.center.util;

import com.codejava.center.domain.enums.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * أدوات موحّدة للتعامل مع المبالغ المالية.
 * الهدف: ضمان أن كل مبلغ في النظام له نفس عدد الخانات العشرية ونفس طريقة التقريب،
 * وأن العرض في الواجهة لا يُظهر أرقاماً مثل 99.99000000000001
 *
 * <h2>العملة</h2>
 *
 * <p>عملة السنتر تُختار من شاشة الإعدادات وتُحفظ في {@code CenterSettings}. هي محفوظة
 * هنا في حقل ساكن - لا تُقرأ من قاعدة البيانات عند كل مبلغ - لأن {@link #formatWithCurrency}
 * يُستدعى في كل خلية جدول وكل سطر تقرير، وقراءةٌ لكل واحد منها تعني استعلاماً لكل صفّ
 * على الشاشة. {@code CurrencyInitializer} يملأ الحقل عند الإقلاع ويحدّثه بعد كل حفظ
 * للإعدادات، تماماً كما يفعل {@link I18n} مع اللغة ولنفس السبب: الـ enums وطبقة
 * الخدمات على خيوط ForkJoinPool لا تقبل الحقن.</p>
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

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private static volatile Currency currency = Currency.DEFAULT;

    private MoneyUtils() {
    }

    /** عملة السنتر الحالية */
    public static Currency currency() {
        return currency;
    }

    /**
     * يضبط عملة السنتر لهذه الجلسة.
     *
     * <p>{@code null} يعني عملة غير مضبوطة في القاعدة - قاعدة مُرقّاة أو تركيب جديد -
     * فتُستعمل {@link Currency#DEFAULT}. لا يُستدعى من الشاشات: مصدر القيمة هو
     * {@code CenterSettings} وحده، وضبطها من مكانين يجعل جهازاً يعرض غير ما يعرضه جاره.</p>
     */
    public static void setCurrency(Currency value) {
        currency = value == null ? Currency.DEFAULT : value;
    }

    /** رمز العملة الحالية بلغة الواجهة الحالية */
    public static String currencySymbol() {
        return currency.getSymbol();
    }

    /** ضبط المبلغ على خانتين عشريتين، مع اعتبار null صفراً */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    /** تنسيق المبلغ للعرض في الواجهة (بدون صيغة أسّية) */
    public static String format(BigDecimal value) {
        return normalize(value).toPlainString();
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
