package com.codejava.center.fxml;

import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حارس الوصل بين ملف الشاشة ومتحكّمها.
 *
 * <p>{@code FxmlWellFormedTest} يفحص أن الملف XML سليم، وهو لا يكفي: الملف السليم نحوياً
 * قد يشير إلى دالة غير موجودة فتسقط الشاشة بـ {@code LoadException} عند فتحها، أو يحمل
 * {@code fx:id} مختلفاً بحرف عن اسم الحقل فيبقى الحقل {@code null} حتى يضغط المستخدم زراً
 * فيأتي {@code NullPointerException} بلا أي أثر يدلّ على السبب.</p>
 *
 * <p>وكلاهما لا يظهر في البناء ولا في بقية الاختبارات: الشاشات لا تُحمَّل إلا وقت التشغيل،
 * ولا يحمّلها خادم البناء لأن ذلك يحتاج تشغيل JavaFX. الفحص هنا بالانعكاس، بلا نافذة.</p>
 */
class FxmlControllerWiringTest {

    private static final Path FXML_DIR = Path.of("src/main/resources/fxml");

    private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([^\"]+)\"");
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([^\"]+)\"");

    /** كل معالِج حدث: onAction و onKeyPressed و onMouseClicked … */
    private static final Pattern HANDLER = Pattern.compile("\\son[A-Za-z]+=\"#([^\"]+)\"");

    @Test
    void everyHandlerReferencedByFxmlExistsInItsController() throws Exception {
        List<String> unresolved = new ArrayList<>();

        for (Path file : fxmlFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Class<?> controller = controllerOf(content);
            if (controller == null) {
                continue;
            }

            Set<String> methods = Arrays.stream(controller.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(Collectors.toCollection(TreeSet::new));

            Matcher matcher = HANDLER.matcher(content);
            while (matcher.find()) {
                if (!methods.contains(matcher.group(1))) {
                    unresolved.add(file.getFileName() + " -> #" + matcher.group(1));
                }
            }
        }

        assertThat(unresolved).as("دوال يشير إليها FXML ولا وجود لها في المتحكّم").isEmpty();
    }

    /**
     * الاتجاه الآخر: حقل {@code @FXML} بلا {@code fx:id} مقابل يبقى {@code null} بصمت.
     * هذه هي حالة الخطأ المطبعي في اسم واحد من عشرين حقلاً في شاشة واحدة.
     */
    @Test
    void everyInjectedFieldHasAMatchingFxIdInItsScreen() throws Exception {
        List<String> unresolved = new ArrayList<>();

        for (Path file : fxmlFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Class<?> controller = controllerOf(content);
            if (controller == null) {
                continue;
            }

            Set<String> ids = new TreeSet<>();
            Matcher matcher = FX_ID.matcher(content);
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }

            for (Field field : controller.getDeclaredFields()) {
                if (field.isAnnotationPresent(FXML.class) && !ids.contains(field.getName())) {
                    unresolved.add(controller.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertThat(unresolved).as("حقول @FXML بلا fx:id مقابل، تبقى null عند فتح الشاشة").isEmpty();
    }

    private List<Path> fxmlFiles() throws IOException {
        try (Stream<Path> files = Files.list(FXML_DIR)) {
            return files.filter(path -> path.toString().endsWith(".fxml")).toList();
        }
    }

    private Class<?> controllerOf(String content) throws ClassNotFoundException {
        Matcher matcher = CONTROLLER.matcher(content);
        return matcher.find() ? Class.forName(matcher.group(1)) : null;
    }
}
