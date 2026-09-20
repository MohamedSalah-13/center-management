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

/**
 * حارس حدود طبقة الأعمال.
 *
 * <p>القاعدة مكتوبة في {@code CLAUDE.md} منذ زمن: ما في {@code service/} و{@code domain/}
 * و{@code repository/} و{@code security/} يجب أن يعمل على خادم بلا نافذة ولا سجلّ ويندوز
 * ولا طابعة. لكنها كانت قاعدة يقرأها من يقرأ: لا شيء يمنع استيراد {@code PrintPreferences}
 * في خدمة، والبناء يمرّ، ولا يظهر الخلل إلا يوم يُفصل المشروع إلى وحدة {@code center-app}
 * بلا JavaFX — أي بعد أن يكون التسرّب قد تكاثر في عشرة ملفات.</p>
 *
 * <p>هذا الاختبار هو الفرض الآلي إلى أن يقوم به فصل الوحدات نفسه: يقرأ الاستيرادات نصّاً،
 * لأن ما يُفحص هو ما يستطيع الملف رؤيته لا ما ينفّذه فعلاً.</p>
 *
 * <p>وكل قاعدة هنا كُتبت يوم سُدّد دَينها لا قبله — قاعدةٌ تفشل يوم كتابتها تُعطَّل ولا
 * تُصلَح: {@code java.awt} يوم صار {@code WhatsAppLinkSender} يعيد الرابط وتفتحه الواجهة
 * (البند 6)، و{@code javafx} بلا استثناء يوم صارت قفزةُ {@code AlertFeed} إلى خيط الواجهة
 * محقونةً من Desktop (البند 7). <b>ولم يبق استثناء واحد:</b> الحزم الأربع اليوم خالية،
 * وأول من يكسر ذلك يجد البناء ساقطاً باسم ملفه وسطره.</p>
 */
class BusinessLayerPurityTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/codejava/center");

    private static final List<String> BUSINESS_PACKAGES =
            List.of("service", "domain", "repository", "security");

    /** استيراد ممنوع، ومعه سببه — الرسالة وحدها هي ما يقرأه من يكسره بعد سنة */
    private record Rule(Pattern anImport, String reason) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("^\\s*import\\s+javafx\\."),
                    "JavaFX: طبقة الأعمال تعمل على خادم بلا شاشة"),
            new Rule(Pattern.compile("^\\s*import\\s+java\\.util\\.prefs\\."),
                    "تفضيلات الجهاز: سجلّ ويندوز مصدرٌ لا يملكه الخادم — الواجهات في center-core"),
            new Rule(Pattern.compile("^\\s*import\\s+java\\.awt\\."),
                    "سطح المكتب: لا متصفح ولا عارض ملفات على خادم — الواجهة تفتح ما يُعاد إليها"),
            new Rule(Pattern.compile("^\\s*import\\s+com\\.codejava\\.center\\.util\\.\\w*Preferences\\s*;"),
                    "تفضيلات الجهاز: تصل عبر واجهة يركّبها الطرف الذي يملكها"),
            new Rule(Pattern.compile("^\\s*import\\s+com\\.codejava\\.center\\.util\\.UserSession\\s*;"),
                    "جلسة JavaFX: المنفّذ يأتي من CurrentActor والمؤسسة من TenantContext"),
            new Rule(Pattern.compile("^\\s*import\\s+com\\.codejava\\.center\\.util\\.MySqlLocator\\s*;"),
                    "فحصُ قرصٍ لمجلدات تركيب ويندوز: مسار الأدوات يصل عبر BackupTarget"),
            new Rule(Pattern.compile("^\\s*import\\s+com\\.codejava\\.center\\.controller\\."),
                    "متحكّم شاشة: الاتجاه من الشاشة إلى الخدمة، لا العكس"));

    @Test
    void businessPackagesDoNotReachForTheScreenOrTheMachine() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String business : BUSINESS_PACKAGES) {
            Path root = SOURCE_ROOT.resolve(business);
            assertThat(root).as("حزمة الأعمال %s", business).isDirectory();

            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    collectViolations(file, violations);
                }
            }
        }

        assertThat(violations)
                .as("استيرادات تكسر حدّ طبقة الأعمال")
                .isEmpty();
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
