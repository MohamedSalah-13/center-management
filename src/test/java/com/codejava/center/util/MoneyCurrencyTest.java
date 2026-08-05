package com.codejava.center.util;

import com.codejava.center.domain.enums.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

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

    private final Currency before = MoneyUtils.currency();
    private final Locale localeBefore = I18n.current();

    @AfterEach
    void restore() {
        MoneyUtils.setCurrency(before);
        I18n.setLocale(localeBefore);
    }

    @Test
    void defaultsToTheEgyptianPoundWhenNoCurrencyIsStored() {
        // قاعدة مُرقّاة من نسخة أقدم لا تحمل قيمة في العمود، وكل مبالغها بالجنيه فعلاً
        MoneyUtils.setCurrency(null);

        assertThat(MoneyUtils.currency()).isEqualTo(Currency.EGP);
    }

    @Test
    void appendsTheSymbolOfTheChosenCurrency() {
        MoneyUtils.setCurrency(Currency.SAR);

        assertThat(MoneyUtils.formatWithCurrency(new BigDecimal("300")))
                .isEqualTo("300.00 " + Currency.SAR.getSymbol());
    }

    /**
     * العملة اختيار السنتر واللغة اختيار الجهاز، والاثنان مستقلان: تيرمينالان يعرضان
     * العملة نفسها بكتابتين في اللحظة نفسها.
     */
    @Test
    void theSameCurrencyReadsInTheLanguageOfEachTerminal() {
        MoneyUtils.setCurrency(Currency.EGP);

        I18n.setLocale(I18n.ARABIC);
        String arabic = MoneyUtils.formatWithCurrency(BigDecimal.TEN);

        I18n.setLocale(I18n.ENGLISH);
        String english = MoneyUtils.formatWithCurrency(BigDecimal.TEN);

        assertThat(arabic).isNotEqualTo(english);
        assertThat(arabic).startsWith("10.00 ");
        assertThat(english).startsWith("10.00 ");
    }

    /**
     * الخانات العشرية شكل التخزين لا خيار عرض: كل عمود مالي {@code DECIMAL(12,2)}،
     * فتبديل العملة لا يصحّ أن يغيّر عدد الخانات ويجعل ما يُعرض مخالفاً لما يُحفظ.
     */
    @Test
    void scaleStaysTheSameWhicheverCurrencyIsChosen() {
        for (Currency currency : Currency.values()) {
            MoneyUtils.setCurrency(currency);

            assertThat(MoneyUtils.format(new BigDecimal("7.005")))
                    .as(currency.name())
                    .isEqualTo("7.01");
        }
    }
}
