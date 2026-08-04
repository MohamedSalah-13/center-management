package com.codejava.center.util;

import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

/**
 * تحميل شاشات FXML في مكان واحد.
 *
 * <p>كان كل موضع تحميل يكرّر {@code setControllerFactory} يدوياً، ثم أضافت الترجمة إليه
 * {@code setResources} واتجاه الواجهة. ثلاثة أشياء يجب ألّا يُنسى أيٌّ منها: نسيان الأول
 * يكسر الحقن، ونسيان الثاني يجعل الشاشة تعرض {@code %keys} حرفياً، ونسيان الثالث يترك
 * شاشة عربية مرصوصة من اليسار.</p>
 *
 * <p>{@code showLogin} و{@code showDashboard} تضبطان النافذة نفسها (المقاس، العنوان، الحد
 * الأدنى) لأن تبديل اللغة يعيد بناء المشهد من الصفر ويحتاج نفس الضبط بالضبط.</p>
 */
@Component
@RequiredArgsConstructor
public class ViewLoader {

    public static final String LOGIN_FXML = "/fxml/Login.fxml";
    public static final String DASHBOARD_FXML = "/fxml/Dashboard.fxml";

    private static final String STYLESHEET = "/css/style.css";

    private static final double LOGIN_WIDTH = 500;
    private static final double LOGIN_HEIGHT = 400;
    private static final double DASHBOARD_WIDTH = 1280;
    private static final double DASHBOARD_HEIGHT = 800;

    // حد أدنى للنافذة: الجداول والمخططات تصبح غير قابلة للقراءة تحته،
    // ولوحة القيادة تحتوي ثلاث بطاقات ومخططين جنباً إلى جنب
    private static final double DASHBOARD_MIN_WIDTH = 1100;
    private static final double DASHBOARD_MIN_HEIGHT = 700;

    private final ApplicationContext applicationContext;

    /** مُحمِّل مضبوط بمصنع المتحكمات وحزمة النصوص - نقطة الدخول لأي تحميل جزئي */
    public FXMLLoader loaderFor(String fxmlPath) {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalStateException(I18n.format("error.fxmlMissing", fxmlPath));
        }
        FXMLLoader loader = new FXMLLoader(resource, I18n.bundle());
        loader.setControllerFactory(applicationContext::getBean);
        return loader;
    }

    /** يحمّل شاشة فرعية لتوضع داخل منطقة المحتوى */
    public <T> T load(String fxmlPath) throws IOException {
        T view = loaderFor(fxmlPath).load();

        // نص الجدول الفارغ يأتي من حزمة JavaFX التي لا تحوي عربية، فيُضبط هنا لكل الجداول
        if (view instanceof Parent parent) {
            Tables.localizeEmptyPlaceholders(parent);
        }
        return view;
    }

    /** يبني مشهداً بالتنسيق والاتجاه الصحيحين للغة الحالية */
    public Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);

        // هذا السطر وحده يقلب الواجهة: JavaFX يعكس مواضع التخطيط ومحاذاة المحتوى معاً،
        // فلا يحتاج التنسيق قواعد خاصة بكل اتجاه - وكتابتها تقلب النتيجة مرتين
        scene.setNodeOrientation(orientation());

        URL stylesheet = getClass().getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        return scene;
    }

    public static NodeOrientation orientation() {
        return I18n.isRightToLeft() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
    }

    /** يعرض شاشة الدخول في النافذة المعطاة بمقاسها الصغير */
    public void showLogin(Stage stage) throws IOException {
        Parent root = load(LOGIN_FXML);

        // رفع قيود لوحة القيادة قبل عرض واجهة 500×400، وإلا بقيت النافذة مكبّرة
        // وبحد أدنى 1100×700 حول محتوى صغير
        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);

        stage.setScene(scene(root, LOGIN_WIDTH, LOGIN_HEIGHT));
        stage.setTitle(I18n.get("login.windowTitle"));
        stage.setWidth(LOGIN_WIDTH);
        stage.setHeight(LOGIN_HEIGHT);
        stage.centerOnScreen();
    }

    /** يعرض لوحة القيادة في النافذة المعطاة مكبَّرة */
    public void showDashboard(Stage stage) throws IOException {
        Parent root = load(DASHBOARD_FXML);

        stage.setScene(scene(root, DASHBOARD_WIDTH, DASHBOARD_HEIGHT));
        stage.setTitle(I18n.get("app.title"));
        stage.setMinWidth(DASHBOARD_MIN_WIDTH);
        stage.setMinHeight(DASHBOARD_MIN_HEIGHT);
        stage.setMaximized(true);
        stage.centerOnScreen();
    }
}
