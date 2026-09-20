package com.codejava.center;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ما لا يستطيع فصلُ الوحدات منعه.
 *
 * <p>هذا الاختبار كان يحرس أربع حزم داخل وحدة واحدة، ويفشل البناء لاستيراد {@code javafx}
 * أو متحكّمٍ أو تفضيلاتِ جهاز. **ومعظم ذلك صار شرطَ تجميع:** {@code center-app} لا يرى
 * {@code center-desktop} أصلاً، ولا JavaFX في شجرة اعتمادياته — فما لا يوجد لا يُستورَد،
 * ولا حاجة إلى قاعدة تقول ذلك.</p>
 *
 * <p>لكن حزمَ الـ JDK موجودة في كل مكان: {@code java.awt} و{@code javax.print}
 * و{@code javax.sound} و{@code java.util.prefs} تُستورد هنا بلا أن يعترض مُترجِم ولا pom.
 * وكلّها تفترض جهازاً أمام إنسان: سطحَ مكتب يفتح ملفاً، وطابعةً موصولة، وسمّاعةً،
 * وسجلَّ ويندوز. هذه هي ما بقي لهذا الاختبار.</p>
 *
 * <p><b>وبلا استثناء واحد.</b> كان آخرها {@code util/I18n} يقرأ لغةَ الجهاز من
 * {@code java.util.prefs}؛ صار المصدر {@code LocaleProvider} في النواة يركّبه سطحُ
 * المكتب من {@code LanguagePreferences}، فسقط الاستثناء معه. وقاعدةٌ تفشل يوم كتابتها
 * تُعطَّل ولا تُصلَح: كل سطر هنا أُضيف يوم سُدّد دَينه.</p>
 */
class BusinessLayerPurityTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/codejava/center");

    /** استيراد ممنوع، ومعه سببه — الرسالة وحدها هي ما يقرأه من يكسره بعد سنة */
    private record Rule(Pattern anImport, String reason) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("^\\s*import\\s+java\\.awt\\."),
                    "سطح المكتب: لا متصفح ولا عارض ملفات على خادم — الواجهة تفتح ما يُعاد إليها"),
            new Rule(Pattern.compile("^\\s*import\\s+javax\\.print\\."),
                    "طابعة موصولة: الخدمة تملأ الورقة، ومن يملك جهازاً يسلّمها"),
            new Rule(Pattern.compile("^\\s*import\\s+javax\\.sound\\."),
                    "سمّاعة: الصوت إشعارٌ لمن أمام الشاشة، ولا أحد أمام الخادم"),
            new Rule(Pattern.compile("^\\s*import\\s+java\\.util\\.prefs\\."),
                    "تفضيلات الجهاز: سجلّ ويندوز مصدرٌ لا يملكه الخادم — الواجهات في center-core"));

    /**
     * معيار انتهاء فصل الوحدة، منفَّذاً لا موصوفاً.
     *
     * <p>الخطة تقول: "{@code center-app} يُجمَّع ويُختبر بلا {@code javafx-*} في شجرة
     * اعتمادياته". وشجرة الاعتماديات شيء يُقرأ مرة يوم الفصل ثم يُنسى — ويكفي أن يضيف
     * أحدهم اعتماداً يجرّ JavaFX بالتبعية ليصير الفصل اسماً بلا معنى. فحصُه هنا يجعله
     * شرطاً دائماً: لو دخلت JavaFX الشجرة يوماً، سقط هذا السطر باسمه.</p>
     */
    @Test
    void javaFxIsNotEvenOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("javafx.application.Platform"))
                .as("JavaFX في شجرة اعتماديات طبقة الأعمال")
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void theBusinessLayerDoesNotReachForTheMachine() throws IOException {
        List<String> violations = new ArrayList<>();
        assertThat(SOURCE_ROOT).as("شجرة مصدر طبقة الأعمال").isDirectory();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                collectViolations(file, violations);
            }
        }

        assertThat(violations).as("استيرادات تفترض جهازاً أمام إنسان").isEmpty();
    }

    private void collectViolations(Path file, List<String> violations) throws IOException {
        String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
        List<String> lines = Files.readAllLines(file);

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            // الاستيرادات في رأس الملف؛ أول تصريح صنف يعني أن ما بعده نصّ لا استيراد
            if (line.startsWith("public ") || line.startsWith("class ")) {
                return;
            }
            for (Rule rule : RULES) {
                if (rule.anImport().matcher(line).find()) {
                    violations.add("%s:%d — %s (%s)"
                            .formatted(relative, index + 1, line.trim(), rule.reason()));
                }
            }
        }
    }
}
