package com.codejava.center.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * كل مفتاح تطلبه هذه الوحدة موجود في الحزمة.
 *
 * <p>{@code MessageBundleTest} يفحص Java وFXML، ولا يرى JS ولا HTML. والترجمة تفشل
 * صامتة دائماً: مفتاحٌ مخطوء يظهر على الشاشة {@code !some.key!} ولا يُسقط بناءً ولا
 * اختباراً - فيصل إلى السنتر بطاقةً فيها اسمُ مفتاح بدل كلمة.</p>
 *
 * <p>وهو هنا لا هناك لأن موضوعه هنا: {@code CLAUDE.md} يقول إن الاختبار يسكن الوحدة
 * التي تحمل موضوعه، وموضوع هذا الملف ملفّا {@code static/} وأصناف هذه الوحدة.</p>
 */
class WebMessageKeysTest {

    private static final Path BUNDLE =
            Path.of("..", "center-app", "src/main/resources/i18n/messages.properties");

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static final Path JAVA = Path.of("src/main/java");

    /** {@code t('web.x')} في JS */
    private static final Pattern IN_SCRIPT = Pattern.compile("\\bt\\('([a-zA-Z0-9_.]+)'");

    /** {@code data-text="web.x"} و{@code data-placeholder="web.x"} في HTML */
    private static final Pattern IN_MARKUP =
            Pattern.compile("data-(?:text|placeholder)=\"([a-zA-Z0-9_.]+)\"");

    /** {@code I18n.get("...")} و{@code I18n.format("...", …)} في Java */
    private static final Pattern IN_JAVA =
            Pattern.compile("I18n\\.(?:get|format)\\(\"([a-zA-Z0-9_.]+)\"");

    @Test
    void everyKeyTheBrowserAsksForExistsInTheBundle() throws IOException {
        Properties bundle = load();
        Set<String> used = keysIn(STATIC, ".js", IN_SCRIPT);
        used.addAll(keysIn(STATIC, ".html", IN_MARKUP));

        assertThat(used).isNotEmpty();
        assertThat(used).allSatisfy(key ->
                assertThat(bundle.containsKey(key))
                        .as("مفتاح تطلبه واجهة الويب ولا وجود له في الحزمة: " + key)
                        .isTrue());
    }

    @Test
    void everyKeyThisModuleAsksForExistsInTheBundle() throws IOException {
        Properties bundle = load();
        Set<String> used = keysIn(JAVA, ".java", IN_JAVA);

        assertThat(used).isNotEmpty();
        assertThat(used).allSatisfy(key ->
                assertThat(bundle.containsKey(key))
                        .as("مفتاح تطلبه أصناف center-web ولا وجود له في الحزمة: " + key)
                        .isTrue());
    }

    /**
     * ما تُسلّمه {@code /api/messages} هو بادئة {@code web.} وحدها، فكلُّ مفتاح
     * تطلبه الواجهة يجب أن يحمل تلك البادئة - وإلا كان موجوداً في الحزمة وغائباً
     * عن الجواب، وهو فشلٌ لا يلتقطه الاختباران أعلاه.
     */
    @Test
    void theBrowserOnlyAsksForKeysThatTheMessagesEndpointSends() throws IOException {
        Set<String> used = keysIn(STATIC, ".js", IN_SCRIPT);
        used.addAll(keysIn(STATIC, ".html", IN_MARKUP));

        assertThat(used).allSatisfy(key ->
                assertThat(key)
                        .as("مفتاح لا تُسلّمه /api/messages: " + key)
                        .startsWith("web."));
    }

    private Set<String> keysIn(Path root, String extension, Pattern pattern) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(extension)).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    /** الحزمة UTF-8: {@code Properties.load(InputStream)} يقرأ ISO-8859-1 فيقلب العربية */
    private Properties load() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(BUNDLE), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
