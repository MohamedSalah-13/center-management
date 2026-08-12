package com.codejava.center.util;

import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

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
    public static final String INITIAL_SETUP_FXML = "/fxml/InitialSetup.fxml";
    public static final String DASHBOARD_FXML = "/fxml/Dashboard.fxml";

    private static final String STYLESHEET = "/css/style.css";

    private static final double LOGIN_WIDTH = 500;
    private static final double LOGIN_HEIGHT = 400;
    private static final double INITIAL_SETUP_WIDTH = 560;
    private static final double INITIAL_SETUP_HEIGHT = 590;
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

    /** يبني مشهداً بالتنسيق والاتجاه والمقاس الصحيحين للغة الحالية وتفضيل هذا الجهاز */
    public Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);

        // هذا السطر وحده يقلب الواجهة: JavaFX يعكس مواضع التخطيط ومحاذاة المحتوى معاً،
        // فلا يحتاج التنسيق قواعد خاصة بكل اتجاه - وكتابتها تقلب النتيجة مرتين
        scene.setNodeOrientation(orientation());

        URL stylesheet = getClass().getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        // وهذا السطر وحده يضبط مقاسها: حجم خط الجذر الذي تُقاس عليه كل em في الملف،
        // ومعه اختصارات Ctrl + / - / 0 على كل شاشة بما فيها شاشة الدخول
        UiScale.install(scene);
        return scene;
    }

    public static NodeOrientation orientation() {
        return I18n.isRightToLeft() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
    }

    /**
     * يفتح شاشة في نافذة مستقلة تابعة للنافذة التي فتحتها، ويعود بعد إغلاقها.
     *
     * <p>النافذة المستقلة مشهدٌ آخر لا يرث من مشهد الشاشة التي فتحته شيئاً - لا اتجاهاً
     * ولا تنسيقاً ولا حجم خط - وهو نفس سبب وجود {@code Dialogs.decorate}. المرور من هنا
     * هو ما يضمن الثلاثة معاً، فلا يُنسى واحد منها.</p>
     *
     * <p>{@code prepare} تُستدعى على المتحكّم بعد بنائه وقبل عرض النافذة: النافذة تُفتح
     * لصفٍّ بعينه، وتمرير موضوعها بعد العرض يعني ومضةً بشاشة فارغة.</p>
     */
    public <C> void showModal(String fxmlPath, String title, Window owner,
                              double width, double height, Consumer<C> prepare) throws IOException {
        FXMLLoader loader = loaderFor(fxmlPath);
        Parent root = loader.load();
        Tables.localizeEmptyPlaceholders(root);

        if (prepare != null) {
            prepare.accept(loader.getController());
        }

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setScene(scene(root,
                fitToScreen(UiScale.scaled(width), true),
                fitToScreen(UiScale.scaled(height), false)));
        stage.centerOnScreen();
        stage.showAndWait();
    }

    /** يعرض شاشة الدخول في النافذة المعطاة بمقاسها الصغير */
    public void showLogin(Stage stage) throws IOException {
        Parent root = load(LOGIN_FXML);

        // رفع قيود لوحة القيادة قبل عرض واجهة 500×400، وإلا بقيت النافذة مكبّرة
        // وبحد أدنى 1100×700 حول محتوى صغير
        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);

        double width = fitToScreen(UiScale.scaled(LOGIN_WIDTH), true);
        double height = fitToScreen(UiScale.scaled(LOGIN_HEIGHT), false);

        stage.setScene(scene(root, width, height));
        stage.setTitle(I18n.get("login.windowTitle"));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.centerOnScreen();
    }

    /** يعرض معالج إنشاء أول مدير، ولا يصل إليه التطبيق بعد وجود أي مستخدم */
    public void showInitialSetup(Stage stage) throws IOException {
        Parent root = load(INITIAL_SETUP_FXML);

        stage.setMaximized(false);
        stage.setMinWidth(0);
        stage.setMinHeight(0);

        double width = fitToScreen(UiScale.scaled(INITIAL_SETUP_WIDTH), true);
        double height = fitToScreen(UiScale.scaled(INITIAL_SETUP_HEIGHT), false);

        stage.setScene(scene(root, width, height));
        stage.setTitle(I18n.get("setup.windowTitle"));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.centerOnScreen();
    }

    /** يعرض لوحة القيادة في النافذة المعطاة مكبَّرة */
    public void showDashboard(Stage stage) throws IOException {
        Parent root = load(DASHBOARD_FXML);

        stage.setScene(scene(root, DASHBOARD_WIDTH, DASHBOARD_HEIGHT));
        stage.setTitle(I18n.get("app.title"));
        stage.setMinWidth(fitToScreen(UiScale.scaled(DASHBOARD_MIN_WIDTH), true));
        stage.setMinHeight(fitToScreen(UiScale.scaled(DASHBOARD_MIN_HEIGHT), false));
        stage.setMaximized(true);
        stage.centerOnScreen();
    }

    /**
     * يمنع أن يخرج مقاسٌ مكبَّر عن الشاشة.
     *
     * <p>المقاسات أعلاه تكبر مع حجم الخط وإلا ضاق الحدّ الأدنى عن محتواه، لكن 1100 عند
     * 175% تصير 1925 بكسل: على شاشة 1366 يصير الحدّ الأدنى أكبر من الشاشة، فلا تُصغَّر
     * النافذة ولا يُرى طرفها ولا زرّ الإغلاق. الشاشة هي السقف دائماً.</p>
     */
    private static double fitToScreen(double requested, boolean horizontal) {
        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        return Math.min(requested, horizontal ? visual.getWidth() : visual.getHeight());
    }
}
