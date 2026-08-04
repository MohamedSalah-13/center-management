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
}
