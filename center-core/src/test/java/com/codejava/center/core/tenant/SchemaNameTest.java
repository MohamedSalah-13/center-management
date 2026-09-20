package com.codejava.center.core.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * الحارس الوحيد بين نصٍّ يكتبه إنسان وأمر DDL.
 *
 * <p>أسماء الكيانات في SQL لا تُمرَّر كقيم مُعلَّمة، فاسم قاعدة المؤسسة يُلصق في
 * {@code CREATE DATABASE} و{@code USE} كما هو. ما يمرّ من هنا يُنفَّذ، وما لا يُرفض
 * هنا لا يرفضه شيء بعده.</p>
 */
class SchemaNameTest {

    @Test
    void acceptsAPlainLowercaseName() {
        assertThat(SchemaName.of("center_cairo").value()).isEqualTo("center_cairo");
    }

    /**
     * MySQL على ويندوز لا تفرّق بين حالتَي الحرف في أسماء القواعد وعلى لينكس تفرّق:
     * اسمٌ يُحفظ بحالتين يصير قاعدتين على خادمين، وهو خطأ يظهر يوم الترحيل لا يوم الإنشاء.
     */
    @Test
    void normalisesCaseAndSurroundingSpace() {
        assertThat(SchemaName.of("  Center_Cairo  ").value()).isEqualTo("center_cairo");
    }

    @Test
    void refusesEverythingThatIsNotALetterDigitOrUnderscore() {
        assertThatThrownBy(() -> SchemaName.of("center cairo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("center-cairo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("center.cairo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("سنتر"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** الشكل الذي وُجد هذا الصنف لأجله: نصّ يحمل عبارةً بعد اسم */
    @Test
    void refusesAnythingCarryingAStatementAfterTheName() {
        assertThatThrownBy(() -> SchemaName.of("cairo`; DROP DATABASE `mysql"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("cairo; DROP DATABASE mysql"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("cairo`"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANameThatDoesNotStartWithALetter() {
        assertThatThrownBy(() -> SchemaName.of("1cairo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("_cairo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** قواعد يملكها الخادم، وقاعدة المنصة: مؤسسةٌ تأخذ اسمها تكتب في سجلّ من هي المؤسسات */
    @Test
    void refusesTheNamesTheServerAndThePlatformOwn() {
        assertThatThrownBy(() -> SchemaName.of("mysql"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("information_schema"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("Center_Platform"))
                .as("المحجوز يُفحص بعد التطبيع، وإلا مرّ بحروف كبيرة")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesNamesTooShortOrTooLongForTheServerAndItsTools() {
        assertThatThrownBy(() -> SchemaName.of("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("c".repeat(49)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SchemaName.of("c".repeat(48)).value()).hasSize(48);
    }

    @Test
    void refusesNothingAtAll() {
        assertThatThrownBy(() -> SchemaName.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
