package com.codejava.center.i18n;

import com.codejava.center.domain.enums.NotificationType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.util.DocumentKind;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PrintPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حارس حزم النصوص.
 *
 * <p>الترجمة تفشل بصمت لا بخطأ ترجمة: مفتاح منسيّ في الملف الإنجليزي يظهر للمستخدم
 * بالعربية، ومفتاح مكتوب خطأً في الكود يظهر كـ {@code !some.key!} على الشاشة، وكلاهما
 * لا يمنع البناء. هذه الاختبارات تحوّل الحالتين إلى فشل بناء.</p>
 *
 * <p>تُقرأ الملفات مباشرةً لا عبر {@code ResourceBundle}: الحزمة الإنجليزية ترث مفاتيح
 * الحزمة العربية عند القراءة العادية، فتبدو مكتملة دائماً ولو كانت فارغة.</p>
 */
class MessageBundleTest {

    private static final Path ARABIC = Path.of("src/main/resources/i18n/messages.properties");
    private static final Path ENGLISH = Path.of("src/main/resources/i18n/messages_en.properties");
    private static final Path FXML_DIR = Path.of("src/main/resources/fxml");
    private static final Path JAVA_DIR = Path.of("src/main/java");

    /** المفاتيح المشار إليها في FXML: text="%key" و promptText="%key" */
    private static final Pattern FXML_KEY = Pattern.compile("=\"%([^\"]+)\"");

    /**
     * الاستدعاءات ذات المفتاح الحرفي وحدها.
     * اشتراط {@code ,} أو {@code )} بعد النص مقصود: بدونه يلتقط النمط بادئة المفاتيح
     * المركَّبة مثل {@code I18n.get("role." + name())} فيبلّغ عن "role." كمفتاح مفقود.
     * تلك المفاتيح تُفحص في {@link #everyEnumConstantHasADisplayName()}.
     */
    private static final Pattern JAVA_KEY =
            Pattern.compile("I18n\\.(?:get|format)\\(\\s*\"([^\"]+)\"\\s*[,)]");

    /** أعلى رقم وسيط في نص MessageFormat، مثل {0} و {2} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    @Test
    void arabicAndEnglishBundlesDeclareTheSameKeys() throws IOException {
        Set<String> arabic = keysOf(ARABIC);
        Set<String> english = keysOf(ENGLISH);

        assertThat(missing(arabic, english))
                .as("مفاتيح موجودة بالعربية وغائبة عن messages_en.properties")
                .isEmpty();
        assertThat(missing(english, arabic))
                .as("مفاتيح موجودة بالإنجليزية وغائبة عن messages.properties")
                .isEmpty();
    }

    @Test
    void everyKeyReferencedByFxmlExists() throws IOException {
        Set<String> declared = keysOf(ARABIC);
        List<String> unresolved = new ArrayList<>();

        try (Stream<Path> files = Files.list(FXML_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = FXML_KEY.matcher(content);
                while (matcher.find()) {
                    if (!declared.contains(matcher.group(1))) {
                        unresolved.add(file.getFileName() + " -> %" + matcher.group(1));
                    }
                }
            }
        }

        assertThat(unresolved).as("مفاتيح تشير إليها شاشات FXML ولا وجود لها في الحزمة").isEmpty();
    }

    @Test
    void everyKeyReferencedByJavaCodeExists() throws IOException {
        Set<String> declared = keysOf(ARABIC);
        List<String> unresolved = new ArrayList<>();

        try (Stream<Path> files = Files.walk(JAVA_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = JAVA_KEY.matcher(content);
                while (matcher.find()) {
                    if (!declared.contains(matcher.group(1))) {
                        unresolved.add(file.getFileName() + " -> " + matcher.group(1));
                    }
                }
            }
        }

        assertThat(unresolved).as("مفاتيح يستدعيها الكود ولا وجود لها في الحزمة").isEmpty();
    }

    /**
     * المفاتيح المركَّبة من أسماء الـ enums لا يلتقطها المسح النصّي،
     * فتُفحص هنا صراحةً - وهي بالضبط المواضع التي تُنسى عند إضافة قيمة جديدة.
     */
    @Test
    void everyEnumConstantHasADisplayName() throws IOException {
        Set<String> declared = keysOf(ARABIC);
        List<String> unresolved = new ArrayList<>();

        for (Role role : Role.values()) {
            requireKey(declared, "role." + role.name(), unresolved);
        }
        for (NotificationType type : NotificationType.values()) {
            requireKey(declared, "notificationType." + type.name(), unresolved);
        }
        for (TransactionType type : TransactionType.values()) {
            requireKey(declared, "transactionType." + type.name(), unresolved);
        }
        for (PrintPreferences.PrintMode mode : PrintPreferences.PrintMode.values()) {
            requireKey(declared, "printMode." + mode.name(), unresolved);
        }
        for (DocumentKind kind : DocumentKind.values()) {
            requireKey(declared, "documentKind." + kind.name(), unresolved);
        }
        // نوع العمولة نص حر في قاعدة البيانات لا enum، والقيم الثلاث هي ما تعرضه الشاشة
        for (String commission : new String[]{"PERCENTAGE", "FIXED_AMOUNT", "RENT"}) {
            requireKey(declared, "commission." + commission, unresolved);
        }

        assertThat(unresolved).as("قيم enum بلا اسم معروض في الحزمة").isEmpty();
    }

    /**
     * اختلاف عدد الوسائط بين اللغتين يعني أن الترجمة الإنجليزية ستُسقط قيمة
     * (اسم طالب، مبلغ) أو تعرض {0} حرفياً - وكلاهما يظهر عند العميل فقط.
     */
    @Test
    void translationsUseTheSamePlaceholders() throws IOException {
        Properties arabic = load(ARABIC);
        Properties english = load(ENGLISH);
        List<String> mismatched = new ArrayList<>();

        for (String key : arabic.stringPropertyNames()) {
            int arabicArgs = placeholderCount(arabic.getProperty(key));
            int englishArgs = placeholderCount(english.getProperty(key, ""));
            if (arabicArgs != englishArgs) {
                mismatched.add(key + " (ar=" + arabicArgs + ", en=" + englishArgs + ")");
            }
        }

        assertThat(mismatched).as("نصوص يختلف عدد وسائطها بين اللغتين").isEmpty();
    }

    /**
     * العربية حزمة الأساس (بلا لاحقة)، و{@code ResourceBundle} يجرّب لغة الـ JVM
     * الافتراضية قبل الأساس. على ويندوز إنجليزي كان ذلك يجعل طلب العربية يقع على
     * {@code messages_en} فيعمل البرنامج كله بالإنجليزية رغم ضبطه على العربية.
     */
    @Test
    void arabicIsHonouredEvenWhenTheJvmDefaultLocaleIsEnglish() {
        Locale jvmDefault = Locale.getDefault();
        Locale chosenBefore = I18n.current();
        try {
            Locale.setDefault(Locale.US);
            I18n.setLocale(I18n.ARABIC);

            assertThat(I18n.get("common.error")).isEqualTo("خطأ");
            assertThat(I18n.isRightToLeft()).isTrue();
        } finally {
            I18n.setLocale(chosenBefore);
            Locale.setDefault(jvmDefault);
        }
    }

    @Test
    void englishBundleIsUsedWhenEnglishIsChosen() {
        Locale chosenBefore = I18n.current();
        try {
            I18n.setLocale(I18n.ENGLISH);

            assertThat(I18n.get("common.error")).isEqualTo("Error");
            assertThat(I18n.isRightToLeft()).isFalse();
        } finally {
            I18n.setLocale(chosenBefore);
        }
    }

    private void requireKey(Set<String> declared, String key, List<String> unresolved) {
        if (!declared.contains(key)) {
            unresolved.add(key);
        }
    }

    private Set<String> keysOf(Path file) throws IOException {
        return new TreeSet<>(load(file).stringPropertyNames());
    }

    private Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private Set<String> missing(Set<String> from, Set<String> in) {
        Set<String> difference = new TreeSet<>(from);
        difference.removeAll(in);
        return difference;
    }

    /** عدد الوسائط = أعلى رقم وسيط + 1، لأن الترقيم يبدأ من صفر */
    private int placeholderCount(String pattern) {
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        int highest = -1;
        while (matcher.find()) {
            highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
        }
        return highest + 1;
    }
}
