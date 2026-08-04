package com.codejava.center.util;

import javafx.geometry.NodeOrientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Optional;

/**
 * نوافذ التنبيه بلغة الواجهة واتجاهها.
 *
 * <p>يحلّ محل {@code AlertUtils} من مكتبة fx-commons التي كان المشروع يعتمد عليها. واجهتها
 * خمس دوال ساكنة بلا أي منفذ لضبط الاتجاه أو نصوص الأزرار، فكانت نوافذها تُرسم من اليسار
 * لليمين وبأزرار OK/Cancel مهما كانت لغة البرنامج.</p>
 *
 * <p>الصيغة ذات الوسيط الواحد تستعمل عنواناً قياسياً حسب نوع النافذة، وهي المستعملة في
 * أغلب الشاشات؛ وصيغة الوسيطين تبقى لمن يحتاج عنواناً خاصاً.</p>
 */
public final class Dialogs {

    private static final String STYLESHEET = "/css/style.css";

    private Dialogs() {
    }

    public static void info(String content) {
        info(I18n.get("common.info"), content);
    }

    public static void info(String title, String content) {
        show(Alert.AlertType.INFORMATION, title, content);
    }

    public static void success(String content) {
        success(I18n.get("common.success"), content);
    }

    public static void success(String title, String content) {
        show(Alert.AlertType.INFORMATION, title, content);
    }

    public static void warning(String content) {
        warning(I18n.get("common.warning"), content);
    }

    public static void warning(String title, String content) {
        show(Alert.AlertType.WARNING, title, content);
    }

    public static void error(String content) {
        error(I18n.get("common.error"), content);
    }

    public static void error(String title, String content) {
        show(Alert.AlertType.ERROR, title, content);
    }

    public static boolean confirm(String content) {
        return confirm(I18n.get("common.confirm"), content);
    }

    /**
     * سؤال تأكيد بزرَّي نعم/إلغاء مترجمين.
     * لا نعتمد على {@link ButtonType#OK} لأن نصّه يأتي من حزمة JavaFX الداخلية،
     * وترجمتها العربية غير مضمونة في كل توزيعة.
     */
    public static boolean confirm(String title, String content) {
        ButtonType yes = new ButtonType(I18n.get("common.yes"), ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType(I18n.get("common.no"), ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = build(Alert.AlertType.CONFIRMATION, title, content);
        alert.getButtonTypes().setAll(yes, no);

        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == yes;
    }

    private static void show(Alert.AlertType type, String title, String content) {
        build(type, title, content).showAndWait();
    }

    private static Alert build(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null); // الترويسة الافتراضية تكرّر العنوان وتُطيل النافذة بلا فائدة
        alert.setContentText(content);

        DialogPane pane = alert.getDialogPane();
        pane.setNodeOrientation(I18n.isRightToLeft()
                ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);

        // النافذة نافذة مستقلة ولا ترث أنماط المشهد الذي فتحها
        URL stylesheet = Dialogs.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            pane.getStylesheets().add(stylesheet.toExternalForm());
        }
        return alert;
    }
}
