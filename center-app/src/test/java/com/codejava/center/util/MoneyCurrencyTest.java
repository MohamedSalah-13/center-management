package com.codejava.center.util;

import com.codejava.center.core.i18n.LocaleProvider;
import com.codejava.center.domain.enums.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * عملة السنتر في العرض.
 *
 * <p>هذه حالات لا يكشفها المترجِم ولا التشغيل العادي: الرمز يأتي من حزمة نصوص، فبقاؤه
 * على "ج.م" بعد تبديل العملة - أو ظهوره عربياً في واجهة إنجليزية - خطأٌ يظهر في إيصال
 * مطبوع عند العميل لا في شاشة المطوّر.</p>
 *
 * <p>لا نصّ حرفي في التوقّعات: العملة والرمز يُقرآن من الحزمة نفسها، وكتابة "ر.س" هنا
 * تجعل الاختبار يفشل يوم يُصحَّح إملاء الرمز في الترجمة لا يوم ينكسر السلوك.</p>
 */
class MoneyCurrencyTest {

    private final CurrencyProvider before = MoneyUtils.provider();
    private final LocaleProvider localeBefore = I18n.provider();

    @AfterEach
    void restore() {
        MoneyUtils.install(before);
        I18n.install(localeBefore);
    }

    @Test
    void defaultsToTheEgyptianPoundWhenNoCurrencyIsStored() {
        // قاعدة مُرقّاة من نسخة أقدم لا تحمل قيمة في العمود، وكل مبالغها بالجنيه فعلاً
        MoneyUtils.install(() -> null);

        assertThat(MoneyUtils.currency()).isEqualTo(Currency.EGP);
    }

    @Test
    void appendsTheSymbolOfTheChosenCurrency() {
        MoneyUtils.install(() -> Currency.SAR);

        assertThat(MoneyUtils.formatWithCurrency(new BigDecimal("300")))
                .isEqualTo("300.00 " + Currency.SAR.getSymbol());
    }

    /**
     * العملة اختيار السنتر واللغة اختيار الجهاز، والاثنان مستقلان: تيرمينالان يعرضان
     * العملة نفسها بكتابتين في اللحظة نفسها.
     */
    @Test
    void theSameCurrencyReadsInTheLanguageOfEachTerminal() {
        MoneyUtils.install(() -> Currency.EGP);

        I18n.install(() -> I18n.ARABIC);
        String arabic = MoneyUtils.formatWithCurrency(BigDecimal.TEN);

        I18n.install(() -> I18n.ENGLISH);
        String english = MoneyUtils.formatWithCurrency(BigDecimal.TEN);

        assertThat(arabic).isNotEqualTo(english);
        assertThat(arabic).startsWith("10.00 ");
        assertThat(english).startsWith("10.00 ");
    }

    /**
     * العملة تُسأل عند كل مبلغ لا تُلتقط مرة.
     *
     * <p>على جهازٍ يخدم سنتراً واحداً لا فرق: تُقرأ عند الإقلاع وتُحدَّث بعد كل حفظ.
     * وعلى خادمٍ هو الفرق كله - قيمةٌ واحدة في الـ JVM تجعل آخرَ سنترٍ حفظ إعداداته
     * يذيّل مبالغ السناتر الأخرى برمز عملته، وهو خطأٌ يُقرأ على أنه مبلغ صحيح.</p>
     */
    @Test
    void theCurrencyFollowsWhicheverCentreIsAskingRightNow() {
        AtomicReference<Currency> asker = new AtomicReference<>(Currency.EGP);
        MoneyUtils.install(asker::get);

        String egyptian = MoneyUtils.formatWithCurrency(BigDecimal.TEN);
        asker.set(Currency.SAR);
        String saudi = MoneyUtils.formatWithCurrency(BigDecimal.TEN);

        assertThat(egyptian).isNotEqualTo(saudi);
        assertThat(egyptian).endsWith(Currency.EGP.getSymbol());
        assertThat(saudi).endsWith(Currency.SAR.getSymbol());
    }

    /**
     * الخانات العشرية شكل التخزين لا خيار عرض: كل عمود مالي {@code DECIMAL(12,2)}،
     * فتبديل العملة لا يصحّ أن يغيّر عدد الخانات ويجعل ما يُعرض مخالفاً لما يُحفظ.
     */
    @Test
    void scaleStaysTheSameWhicheverCurrencyIsChosen() {
        for (Currency currency : Currency.values()) {
            MoneyUtils.install(() -> currency);

            assertThat(MoneyUtils.format(new BigDecimal("7.005")))
                    .as(currency.name())
                    .isEqualTo("7.01");
        }
    }
}
