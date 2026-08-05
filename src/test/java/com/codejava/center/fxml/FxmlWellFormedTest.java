package com.codejava.center.fxml;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * حارس صحّة ملفات FXML كـ XML.
 *
 * <p>الشاشة لا تُحمَّل إلا وقت التشغيل، فملف FXML مكسور لا يمنع البناء ولا الاختبارات: يظهر
 * الخطأ حين يفتح المستخدم الشاشة، وقد يكون ذلك عند العميل. أوقعنا هذا فعلاً بتعليق يحوي
 * {@code --} في داخله، وهو ممنوع في تعليقات XML - الشاشة كانت ستسقط عند فتح الإعدادات
 * بينما الترجمة والاختبارات كلها خضراء.</p>
 *
 * <p>الفحص هنا نحوي فقط (XML سليم): تحميل FXML فعلياً يحتاج تشغيل JavaFX وحقن المتحكمات،
 * وهو ما لا يقوم به خادم البناء.</p>
 */
class FxmlWellFormedTest {

    private static final Path FXML_DIR = Path.of("src/main/resources/fxml");

    @Test
    void everyFxmlFileIsWellFormedXml() throws IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // FXML لا يحمل DTD، وتعطيل الكيانات الخارجية يمنع قراءة الشبكة أثناء البناء
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        List<String> broken = new ArrayList<>();
        try (Stream<Path> files = Files.list(FXML_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                try {
                    factory.newDocumentBuilder().parse(file.toFile());
                } catch (SAXException | IOException e) {
                    broken.add(file.getFileName() + " -> " + e.getMessage());
                }
            }
        }

        org.assertj.core.api.Assertions.assertThat(broken)
                .as("ملفات FXML لا تُقرأ كـ XML سليم وستُسقط شاشتها عند فتحها")
                .isEmpty();
    }

    /**
     * حارس ضدّ {@code -fx-background} في التنسيق المكتوب داخل السطر.
     *
     * <p>الفرق بينها وبين {@code -fx-background-color} حرفان في الكتابة وكارثة في النتيجة.
     * الثانية لون الخلفية المرسوم فعلاً. أما الأولى فلونٌ <b>مرجعيّ</b> تبني عليه
     * {@code modena.css} لونَ النصّ:</p>
     *
     * <pre>-fx-text-background-color: ladder(-fx-background, -fx-light-text-color 45%, -fx-dark-text-color 46%, …)</pre>
     *
     * <p>ولأن {@code transparent} أسودُ بشفافية كاملة، تقرأ {@code ladder} سطوعه صفراً
     * فتختار الأبيض - ويرثه كل ما تحت العقدة. النتيجة شاشة يختفي فيها نصّ كل
     * {@code Label} وكل {@code CheckBox} بينما تبقى الأزرار وخلايا الجداول ظاهرة، لأنها
     * تعلن لون نصّها بنفسها. وقعنا في هذا فعلاً في مركز التنبيهات.</p>
     *
     * <p>وهو عطبٌ لا يُبلَّغ عنه: لا استثناء ولا سطر في السجل، والشاشة تُفتح وتعمل - كل
     * ما هنالك أن كلامها غير مرئي. الخلفية الشفافة لها مكانها الصحيح في
     * {@code .content-scroll} داخل ملف التنسيق، وهي تضبط {@code -fx-background-color}
     * على المنطقة وعلى نافذتها معاً بلا مساس باللون المرجعيّ.</p>
     */
    @Test
    void noInlineStyleOverridesTheReferenceBackgroundColour() throws IOException {
        // النقطتان مباشرةً بعد الاسم: -fx-background-color و -fx-background-radius
        // وأخواتهما تحمل شرطة بعده فلا يلتقطها النمط
        java.util.regex.Pattern poison = java.util.regex.Pattern.compile("-fx-background\\s*:");
        List<String> offenders = new ArrayList<>();

        for (Path directory : List.of(FXML_DIR, Path.of("src/main/java"))) {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.toString();
                    if (!name.endsWith(".fxml") && !name.endsWith(".java")) {
                        continue;
                    }
                    if (poison.matcher(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8))
                            .find()) {
                        offenders.add(file.getFileName().toString());
                    }
                }
            }
        }

        org.assertj.core.api.Assertions.assertThat(offenders)
                .as("-fx-background في تنسيق داخل السطر يقلب نصّ الشاشة إلى أبيض على أبيض؛ "
                        + "المقصود غالباً -fx-background-color")
                .isEmpty();
    }
}
