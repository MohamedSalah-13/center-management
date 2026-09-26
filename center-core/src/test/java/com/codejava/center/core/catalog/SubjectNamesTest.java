package com.codejava.center.core.catalog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SubjectNamesTest {
    @Test void englishAliasesHaveOneKey() {
        for (String alias : new String[]{"E", " English ", "انجليزى", "لغة انجليزية", "اللغة الإنجليزية", "اللُّغَة الإنجليزِيّة"})
            assertThat(SubjectNames.key(alias)).as(alias).isEqualTo("english");
    }
    @Test void arabicAndMathAliasesHaveOneKey() {
        assertThat(SubjectNames.key("عربى")).isEqualTo(SubjectNames.key("اللغة العربية"));
        assertThat(SubjectNames.key("Maths")).isEqualTo(SubjectNames.key("رياضيات"));
    }
    @Test void customNamesIgnoreSpacingDiacriticsAndCase() {
        assertThat(SubjectNames.key("  Robotics  I ")).isEqualTo(SubjectNames.key("robotics i"));
        assertThat(SubjectNames.key("تَرْبِيَة  دينية")).isEqualTo(SubjectNames.key("تربيه دينيه"));
        assertThat(SubjectNames.key("فيزياء")).isNotEqualTo(SubjectNames.key("كيمياء"));
    }
}
