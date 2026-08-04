package com.codejava.center.util;

import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * منفذ الطباعة الوحيد في البرنامج.
 *
 * <p>كانت كل مطبوعة تفتح نافذة الطابعة الخاصة بنظام التشغيل، فيضطر موظف الكاشير إلى
 * الضغط مرتين لكل إيصال ولو كان الجهاز موصولاً بطابعة واحدة طوال اليوم. صار الأسلوب
 * اختياراً في الإعدادات: معاينة، أو نافذة الطابعة، أو طباعة مباشرة
 * ({@link PrintPreferences.PrintMode}).</p>
 *
 * <p><b>يجب استدعاؤه من خيط الواجهة.</b> {@code PrinterJob} في JavaFX لا يعمل على غيره،
 * وهو الاستثناء المعروف من قاعدة "كل عملية بطيئة في الخلفية" الموصوفة في
 * {@link FxAsync}.</p>
 */
public final class Printing {

    private static final String STYLESHEET = "/css/style.css";

    private static final double PREVIEW_WIDTH = 820;
    private static final double PREVIEW_HEIGHT = 900;

    private static final DateTimeFormatter TEST_PAGE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Printing() {
    }

    /**
     * يرسل عقدة للطباعة حسب الأسلوب المضبوط في الإعدادات.
     *
     * @param node  المستند المبنيّ - صفحة كاملة أو إيصال
     * @param owner النافذة التي انطلقت منها الطباعة (نافذة المعاينة تُفتح فوقها)
     */
    public static void print(Node node, Window owner) {
        PrintPreferences.PrintMode mode = PrintPreferences.mode();
        if (mode == PrintPreferences.PrintMode.PREVIEW) {
            showPreview(node, owner);
            return;
        }
        sendToPrinter(node, owner, mode == PrintPreferences.PrintMode.DIALOG);
    }

    /**
     * إرسال فعلي إلى الطابعة مع إنهاء المهمة في كل الحالات.
     * ترك المهمة بلا endJob عند الفشل يُبقيها معلّقة في طابور الطابعة.
     */
    private static void sendToPrinter(Node node, Window owner, boolean showDialog) {
        Printer printer = PrintPreferences.resolvePrinter();
        if (printer == null) {
            throw new IllegalStateException(I18n.get("error.printer.unavailable"));
        }

        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new IllegalStateException(I18n.get("error.printer.unavailable"));
        }

        if (showDialog && !job.showPrintDialog(owner)) {
            job.cancelJob();
            return;
        }

        try {
            job.printPage(node);
        } finally {
            job.endJob();
        }
    }

    /**
     * نافذة معاينة تعرض المستند كما سيخرج من الطابعة، بزرّي طباعة وخيارات الطابعة.
     *
     * <p>المستند نفسه مثبَّت على اتجاه يسار-يمين: الطباعة تجري على عقدة خارج أي مشهد،
     * أي بالاتجاه الافتراضي، فلو ورث محتوى المعاينة اتجاه الواجهة العربي لظهرت المعاينة
     * معكوسة عمّا يخرج من الطابعة - وهو بالضبط ما تُفترض المعاينة أن تمنعه. الشريط
     * حول الورقة وحده يتبع لغة الواجهة لأنه جزء من البرنامج لا من المطبوعة.</p>
     */
    private static void showPreview(Node node, Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.get("print.previewTitle"));

        StackPane sheet = new StackPane(node);
        sheet.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        sheet.setStyle("-fx-background-color: white; -fx-padding: 12;");

        ScrollPane scroll = new ScrollPane(sheet);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white;");

        Label target = new Label(I18n.format("print.previewTarget", describeTarget()));
        target.getStyleClass().add("muted-text");

        Button print = new Button(I18n.get("common.print"));
        print.getStyleClass().add("btn-success");
        Button options = new Button(I18n.get("print.previewOptions"));
        options.getStyleClass().add("btn-secondary");
        Button close = new Button(I18n.get("common.close"));
        close.getStyleClass().add("btn-secondary");

        // الطباعة تجري على العقدة وهي معروضة في المعاينة، وإغلاق النافذة بعدها
        // يتخلّص منها ومن المشهد معاً. الإغلاق مشروط بنجاح الطباعة حتى لا يضيع
        // المستند إن ألغى المستخدم نافذة الطابعة أو فشل الإرسال.
        print.setOnAction(event -> {
            if (printFromPreview(node, stage, false)) {
                stage.close();
            }
        });
        options.setOnAction(event -> {
            if (printFromPreview(node, stage, true)) {
                stage.close();
            }
        });
        close.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, print, options, spacer, close);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setStyle("-fx-padding: 12 18 12 18;");

        HBox header = new HBox(target);
        header.setStyle("-fx-padding: 12 18 0 18;");

        BorderPane root = new BorderPane(scroll, header, null, actions, null);

        Scene scene = new Scene(root, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        scene.setNodeOrientation(ViewLoader.orientation());
        URL stylesheet = Printing.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.showAndWait();
    }

    /** @return هل غادر المستند إلى الطابعة فعلاً */
    private static boolean printFromPreview(Node node, Window preview, boolean showDialog) {
        try {
            sendToPrinter(node, preview, showDialog);
            return true;
        } catch (RuntimeException e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
            return false;
        }
    }

    /** اسم الطابعة التي ستستقبل المهمة، كما يُعرض في المعاينة وفي شاشة الإعدادات */
    public static String describeTarget() {
        Printer printer = PrintPreferences.resolvePrinter();
        return printer == null ? I18n.get("print.noPrinter") : printer.getName();
    }

    /**
     * صفحة تجريبية للتحقق من أن الاختيار في الإعدادات يصل إلى الورق فعلاً.
     * سؤال "هل هذه هي الطابعة الصحيحة؟" لا يُجاب عنه إلا بورقة خارجة منها.
     */
    public static void printTestPage(Window owner) {
        VBox page = new VBox(14);
        page.setStyle("-fx-padding: 40; -fx-background-color: white;");

        Label title = new Label(I18n.get("print.testPageTitle"));
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        Label body = new Label(I18n.format("print.testPageBody",
                describeTarget(), LocalDateTime.now().format(TEST_PAGE_STAMP)));
        body.setWrapText(true);

        page.getChildren().addAll(title, new Separator(), body);
        print(page, owner);
    }
}
