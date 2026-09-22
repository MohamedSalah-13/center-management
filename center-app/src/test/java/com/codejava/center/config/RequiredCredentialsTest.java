package com.codejava.center.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * متغيّرُ بيئةٍ منسيّ: هل يُسمَّى، أم يُترك ليظهر باسم عطلٍ آخر.
 *
 * <p>العطبُ الذي وُجد من أجله هذا الصنف لم يكن في الكود بل في <b>فرضٍ عن الكود</b>:
 * {@code ${DB_PASSWORD}} بلا قيمةٍ افتراضية كُتب على نيّة أن يُسقط الإقلاعَ باسمه، ولم
 * يكن يفعل - كان النصُّ يمضي حرفياً إلى MySQL فترتدّ "كلمة مرور خاطئة". ولذلك يُختبر
 * الاتجاهان معاً: أن الناقصَ يُسمَّى، وأن التامَّ يمرّ.</p>
 */
class RequiredCredentialsTest {

    private static final String PASSWORD = "spring.datasource.password";

    /**
     * قيمةٌ تُولَّد ولا تُكتب.
     *
     * <p>ما يفحصه الاختبار أن العنصر النائب <b>حُلّ</b>، لا ما حُلّ إليه - فأيُّ نصٍّ
     * يفي. وكتابةُ نصٍّ يشبه كلمةَ مرور بجوار متغيّرٍ اسمه {@code DB_PASSWORD} هي
     * بالضبط شكلُ التسريب الحقيقي، فيرفعها ماسحُ الأسرار - وهو محقّ في ذلك.</p>
     *
     * <p>و"إنما هو اختبار" هي الجملةُ التي تُودَع بها الأسرارُ فعلاً: ماسحٌ يتعلّم
     * الناسُ تجاهلَه أسوأ من لا ماسح، وهي نفسُ حجّة الإنذارات الكاذبة في مسح التشغيل.
     * فيُنزع الشكلُ من أصله بدل أن يُستثنى.</p>
     */
    private static final String ANY_VALUE = UUID.randomUUID().toString();

    /**
     * <b>المتغيّرُ الغائب يُسمَّى في الرسالة.</b>
     *
     * <p>وهو كلُّ الفرق: "DB_PASSWORD غير مضبوط" تُصلَح في ثانية، و"Access denied for
     * user" تُرسل صاحبَها يفحص صلاحيات MySQL نصفَ ساعة ثم يعيد ضبط كلمةِ مرورٍ لم تكن
     * خاطئة أصلاً.</p>
     */
    @Test
    void anEnvironmentVariableThatWasNeverSetIsNamedInTheFailure() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PASSWORD, "${DB_PASSWORD}");

        assertThatThrownBy(() -> new RequiredCredentials().postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining(PASSWORD);
    }

    /** وقيمةٌ مضبوطة تمرّ، ولا تُذكر في أيّ رسالة - وإلا طُبع سرٌّ في سجلّ إقلاع */
    @Test
    void aValueThatResolvesPassesAndIsNeverEchoed() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_PASSWORD", ANY_VALUE);
        environment.setProperty(PASSWORD, "${DB_PASSWORD}");

        assertThatCode(() -> new RequiredCredentials().postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    /**
     * <b>وفارغٌ ليس ناقصاً.</b>
     *
     * <p>قاعدةٌ بلا كلمة مرور اختيارٌ قائم - وهو حالُ H2 في كل اختبار في هذا المستودع.
     * ورفضُه هنا يُسقط المجموعةَ كلَّها، وهو بالضبط كيف يُكتب حارسٌ يُطفأ في اليوم
     * التالي بدل أن يُصلَح.</p>
     */
    @Test
    void anEmptyPasswordIsAChoiceNotAnOmission() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PASSWORD, "");

        assertThatCode(() -> new RequiredCredentials().postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    /** ومفتاحٌ لم يُكتب أصلاً ليس شأنَ هذا الفحص: ما يُفحص ما كُتب ولم يصله جواب */
    @Test
    void aPropertyThatWasNeverDeclaredIsNotThisChecksBusiness() {
        assertThatCode(() -> new RequiredCredentials()
                .postProcessEnvironment(new MockEnvironment(), null))
                .doesNotThrowAnyException();
    }

    /**
     * والعنصرُ النائب يُلتقط داخل نصٍّ أطول، لا في أوّله وحده.
     *
     * <p>{@code DB_URL} عنوانٌ فيه مضيفٌ ومنفذٌ ومعاملات، وعنصرٌ نائب في وسطه يذهب إلى
     * سائق MySQL حرفياً فيفشل الاتصالُ برسالةِ عنوانٍ غير صالح.</p>
     */
    @Test
    void aPlaceholderIsFoundInsideALongerValueToo() {
        assertThat(RequiredCredentials.unresolvedVariable(
                "jdbc:mysql://${DB_HOST}:3306/center_db?sslMode=REQUIRED"))
                .isEqualTo("DB_HOST");
    }

    /** والنصُّ التامّ لا يُنتزع منه اسم */
    @Test
    void aFullyResolvedValueNamesNothing() {
        assertThat(RequiredCredentials.unresolvedVariable("jdbc:h2:mem:testdb")).isNull();
        assertThat(RequiredCredentials.unresolvedVariable("")).isNull();
        assertThat(RequiredCredentials.unresolvedVariable(null)).isNull();
    }

    /**
     * ونصٌّ يحمل {@code $} أو قوساً بلا تركيبٍ تامّ ليس عنصراً نائباً.
     *
     * <p>كلمةُ مرورٍ قوية تحمل رموزاً، وقراءةُ {@code p$ssw0rd{} } عنصراً نائباً ناقصاً
     * تمنع الإقلاعَ عن نشرٍ سليمٍ تماماً - وهو عطلٌ أسوأ من الذي وُجد الحارسُ له.</p>
     */
    @Test
    void aPasswordThatMerelyContainsStrangeCharactersIsNotAPlaceholder() {
        assertThat(RequiredCredentials.unresolvedVariable("p$ssw0rd")).isNull();
        assertThat(RequiredCredentials.unresolvedVariable("${unclosed")).isNull();
        assertThat(RequiredCredentials.unresolvedVariable("${}")).isNull();
    }
}
