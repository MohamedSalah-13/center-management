package com.codejava.center.util;

import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * منفذ الطباعة الوحيد في البرنامج: يوزّع المستند على صفحات ويرسله إلى الطابعة.
 *
 * <p>كانت كل مطبوعة تُرسَل كعقدة واحدة إلى {@code printPage}، وهي دالة تطبع ما يسع الصفحة
 * <b>الأولى</b> وتُسقط الباقي بلا خطأ ولا تنبيه. تقرير المتأخرات أو كشف حساب معلم طويل كان
 * يخرج منه أوّله فقط. الآن يوصف المستند كقطع ({@link PrintDocument}) فتُحسب أحجامها وتُوزَّع
 * على صفحات بحيث لا يُقطع سطر في منتصفه.</p>
 *
 * <p>ثلاث مسائل يحلّها هذا الصنف ولا يحلّها {@code printPage} وحده:</p>
 * <ul>
 *   <li><b>الورق:</b> المقاس يأتي من إعدادات النوع ({@link PrintPreferences})، وهو ما يسمح
 *       بطباعة التقارير على A4 والإيصالات على رول 80mm من نفس الجهاز.</li>
 *   <li><b>العرض:</b> ما يزيد عن عرض الورقة يُصغَّر بمعامل واحد لكل المستند بدل أن يُقصّ من
 *       الحافة. التصغير لا يكبّر أبداً: مستند أضيق من الورقة يبقى بحجمه.</li>
 *   <li><b>الطول:</b> التقارير تُقسَّم وتُرقَّم، والإيصالات لا - فهي على رول متصل لا صفحات.</li>
 * </ul>
 *
 * <p><b>يجب استدعاؤه من خيط الواجهة.</b> {@code PrinterJob} في JavaFX لا يعمل على غيره، وهو
 * الاستثناء المعروف من قاعدة "كل عملية بطيئة في الخلفية" الموصوفة في {@link FxAsync}.</p>
 */
public final class Printing {

    private static final String STYLESHEET = "/css/style.css";

    private static final double BLOCK_SPACING = 8;

    // الهوامش بالنقاط (1/72 بوصة) لأنها تُقاس على مساحة الطباعة القادمة من PageLayout.
    // هامش الورقة نفسه من العتاد، وهذا فراغ إضافي داخلها ليتنفّس المحتوى: 36 نقطة ≈ 12 مم
    private static final double REPORT_PADDING = 36;

    // الرول ضيّق أصلاً (58 أو 80 مم)، وهامش التقرير يبتلع ثلث عرضه
    private static final double RECEIPT_PADDING = 8;

    private static final double PREVIEW_WIDTH = 860;
    private static final double PREVIEW_HEIGHT = 900;

    private static final DateTimeFormatter TEST_PAGE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Printing() {
    }

    /**
     * يطبع المستند حسب الأسلوب المضبوط في الإعدادات.
     *
     * @param document المستند موصوفاً بقطعه
     * @param owner    النافذة التي انطلقت منها الطباعة (نافذة المعاينة تُفتح فوقها)
     */
    public static void print(PrintDocument document, Window owner) {
        if (PrintPreferences.mode() == PrintPreferences.PrintMode.PREVIEW) {
            showPreview(document, owner);
            return;
        }
        sendToPrinter(document, owner, PrintPreferences.mode() == PrintPreferences.PrintMode.DIALOG);
    }

    // ---------------------------------------------------------------- الإرسال

    /**
     * إرسال فعلي إلى الطابعة.
     *
     * <p>ترتيب الخطوات مقصود: نافذة الطابعة تُعرض <b>قبل</b> التقسيم لا بعده، لأن المستخدم
     * قد يختار فيها مقاس ورق آخر - والتقسيم المحسوب على A4 لا يصلح لـ A5.</p>
     */
    private static void sendToPrinter(PrintDocument document, Window owner, boolean showDialog) {
        Printer printer = requirePrinter(document.kind());
        PrinterJob job = requireJob(printer);

        PageLayout layout = pageLayoutFor(printer, document.kind());
        job.getJobSettings().setPageLayout(layout);

        if (showDialog) {
            if (!job.showPrintDialog(owner)) {
                job.cancelJob();
                return;
            }
            layout = job.getJobSettings().getPageLayout();
        }

        printPages(job, layout, paginate(document, layout));
    }

    /** يطبع صفحات مبنيّة سلفاً - ما تعرضه نافذة المعاينة بالضبط */
    private static void printBuiltPages(List<Node> pages, PageLayout layout, DocumentKind kind) {
        PrinterJob job = requireJob(requirePrinter(kind));
        job.getJobSettings().setPageLayout(layout);
        printPages(job, layout, pages);
    }

    /**
     * ترك المهمة بلا endJob عند الفشل يُبقيها معلّقة في طابور الطابعة، فالإنهاء في
     * {@code finally} لا في نهاية الحلقة.
     */
    private static void printPages(PrinterJob job, PageLayout layout, List<Node> pages) {
        try {
            for (Node page : pages) {
                if (page.getScene() != null) {
                    // الصفحة معروضة في المعاينة: مبنيّة ومُنسَّقة فعلاً
                    job.printPage(layout, page);
                    continue;
                }
                // صفحة خارج أي مشهد لا تُطبَّق عليها الأنماط ولا يُحسب تخطيطها،
                // فتخرج بخطوط افتراضية ومواضع صفرية. الحاوية المؤقتة تحلّ ذلك.
                Group holder = layoutOffScreen(page);
                job.printPage(layout, page);
                holder.getChildren().clear();
            }
        } finally {
            job.endJob();
        }
    }

    private static Printer requirePrinter(DocumentKind kind) {
        Printer printer = PrintPreferences.resolvePrinter(kind);
        if (printer == null) {
            throw new IllegalStateException(I18n.get("error.printer.unavailable"));
        }
        return printer;
    }

    private static PrinterJob requireJob(Printer printer) {
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new IllegalStateException(I18n.get("error.printer.unavailable"));
        }
        return job;
    }

    /**
     * مقاس الورق المختار لهذا النوع، والعودة إلى تخطيط الطابعة الافتراضي إن رفضته.
     *
     * <p><b>الهوامش من العتاد لا {@code MarginType.DEFAULT}.</b> الافتراضي في JavaFX هو
     * ثلاثة أرباع البوصة على كل جانب - 54 نقطة - وهو رقم مكتوب لورق A4 لا يعرف شيئاً عن
     * الرول. على رول 80 مم (227 نقطة) يبتلع الهامشان 108 نقاط فلا يبقى للطباعة إلا 42 مم،
     * فيخرج الإيصال ضئيلاً في منتصف عرض الورقة. الهامش الفعلي يأتي من الطابعة نفسها،
     * والفراغ حول المحتوى يضيفه {@link #newPage} داخل الورقة.</p>
     */
    private static PageLayout pageLayoutFor(Printer printer, DocumentKind kind) {
        Paper paper = PrintPreferences.resolvePaper(kind, printer);
        if (paper == null) {
            return printer.getDefaultPageLayout();
        }
        PageLayout layout = printer.createPageLayout(
                paper, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        return layout != null ? layout : printer.getDefaultPageLayout();
    }

    // ---------------------------------------------------------------- التقسيم

    /**
     * يوزّع قطع المستند على صفحات مقاس الورقة.
     *
     * <p>القياس يسبق البناء: كل القطع تُوضع في حاوية واحدة داخل مشهد مؤقت لتُحسب أحجامها
     * الفعلية بعد تطبيق الأنماط، ثم تُنقل إلى الصفحات. بغير مشهد تبقى الأبعاد أصفاراً
     * ويصير التقسيم تخميناً.</p>
     */
    private static List<Node> paginate(PrintDocument document, PageLayout layout) {
        boolean splitting = document.kind() == DocumentKind.REPORT;
        double padding = splitting ? REPORT_PADDING : RECEIPT_PADDING;

        double pageWidth = layout.getPrintableWidth();
        double contentWidth = Math.max(1, pageWidth - padding * 2);

        // القطع تُهيَّأ لعرض الورقة قبل قياسها: النص يلتف والصورة تُقيَّد. بغير هذا يُقاس
        // المحتوى بعرضه الطبيعي (نحو 450 نقطة) ثم يُصغَّر ليدخل في 227 نقطة من رول 80 مم،
        // فيخرج الإيصال بنصف حجمه أو أقل. المطلوب أن يُبنى على مقاس الورقة لا أن يُكمَش إليه.
        Node sampleHeader = document.headerFactory() == null ? null : document.headerFactory().get();
        if (sampleHeader != null) {
            fitToWidth(sampleHeader, contentWidth);
        }
        document.blocks().forEach(block -> fitToWidth(block, contentWidth));

        VBox measuring = new VBox(BLOCK_SPACING);
        measuring.setPadding(new Insets(padding));
        measuring.setMinWidth(pageWidth);
        measuring.setPrefWidth(pageWidth);
        measuring.setMaxWidth(pageWidth);
        if (sampleHeader != null) {
            measuring.getChildren().add(sampleHeader);
        }
        Label sampleFooter = pageNumber(1, 1);
        measuring.getChildren().addAll(document.blocks());
        measuring.getChildren().add(sampleFooter);

        Group holder = layoutOffScreen(measuring);

        double headerHeight = sampleHeader == null ? 0 : heightOf(sampleHeader) + BLOCK_SPACING;
        double footerHeight = heightOf(sampleFooter) + BLOCK_SPACING;

        List<Double> blockHeights = new ArrayList<>(document.blocks().size());
        double widest = 0;
        for (Node block : document.blocks()) {
            blockHeights.add(heightOf(block) + BLOCK_SPACING);
            widest = Math.max(widest, block.getLayoutBounds().getWidth());
        }

        // تحرير القطع من حاوية القياس قبل نقلها إلى الصفحات - العقدة لا تقبل أبوين
        measuring.getChildren().clear();
        holder.getChildren().clear();

        double scale = fitScale(widest, contentWidth);
        double heightBudget = layout.getPrintableHeight() / scale - padding * 2;

        List<Integer> perPage = splitting
                ? pageBreaks(blockHeights, headerHeight, footerHeight, heightBudget)
                : List.of(document.blocks().size());

        List<VBox> pages = new ArrayList<>(perPage.size());
        int next = 0;
        for (int count : perPage) {
            VBox page = newPage(document, pageWidth, padding);
            page.getChildren().addAll(document.blocks().subList(next, next + count));
            next += count;
            pages.add(page);
        }

        List<Node> finished = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            VBox built = pages.get(i);
            // "صفحة 1 من 1" ضجيج على مستند من ورقة واحدة
            if (splitting && pages.size() > 1) {
                built.getChildren().add(pageNumber(i + 1, pages.size()));
            }
            if (scale < 1) {
                built.getTransforms().add(new Scale(scale, scale));
            }
            finished.add(built);
        }
        return finished;
    }

    /**
     * توزيع القطع على الصفحات بأطوالها وحدها.
     *
     * <p>معزول عن بناء العقد عمداً: هذا هو المنطق الذي إن أخطأ ضاعت بيانات من المطبوعة بلا
     * أي خطأ ظاهر، وهو خالٍ من JavaFX فيمكن اختباره بلا تشغيل الواجهة. راجع
     * {@code PaginationTest}.</p>
     *
     * <p>القطعة الأطول من الورقة كلها تُترك على صفحتها ويُقصّ ما زاد: الشرط على عدد القطع
     * في الصفحة الحالية هو ما يمنع صفحة فارغة وحلقة لا تنتهي.</p>
     *
     * @return عدد القطع في كل صفحة، بالترتيب. مجموعها يساوي دائماً عدد القطع الداخلة
     */
    static List<Integer> pageBreaks(List<Double> blockHeights, double headerHeight,
                                    double footerHeight, double budget) {
        List<Integer> perPage = new ArrayList<>();
        double used = headerHeight;
        int onPage = 0;

        for (double blockHeight : blockHeights) {
            if (onPage > 0 && used + blockHeight + footerHeight > budget) {
                perPage.add(onPage);
                used = headerHeight;
                onPage = 0;
            }
            used += blockHeight;
            onPage++;
        }

        // الصفحة الأخيرة تُضاف دائماً، ولو فارغة، حتى يخرج مستند بلا قطع بترويسته
        perPage.add(onPage);
        return perPage;
    }

    /**
     * معامل التصغير، وهو <b>شبكة أمان لا وسيلة الملاءمة</b>.
     *
     * <p>الملاءمة تتم برصف المحتوى على عرض الورقة في {@link #fitToWidth}. هذا المعامل لما
     * يستعصي على الالتفاف وحده: كلمة واحدة أطول من الورقة، أو صورة لا تُقيَّد. ولا يكبّر
     * أبداً - مستند أضيق من الورقة يبقى بحجمه ولا يُمَطّ.</p>
     */
    static double fitScale(double widest, double contentWidth) {
        if (widest <= contentWidth || widest <= 0) {
            return 1;
        }
        return contentWidth / widest;
    }

    /**
     * يهيّئ قطعة لعرض الورقة: النص يلتف والصورة تُقيَّد بالعرض المتاح.
     *
     * <p>هذا ما يجعل نفس المستند يصلح على A4 وعلى رول 80 مم: المحتوى يُعاد رصفه على عرض
     * الورقة بخطّه كما هو، بدل أن يُبنى بعرض ثابت ثم يُصغَّر - والتصغير هو ما كان يُخرج
     * الإيصال الحراري ضئيلاً في منتصف الورقة.</p>
     *
     * <p>لا ينزل داخل {@link Label} وأمثاله: أبناؤه من صنع الـ skin الداخلي، والتصرّف فيهم
     * يُبطل ما يفعله {@code wrapText} نفسه.</p>
     */
    private static void fitToWidth(Node node, double contentWidth) {
        if (node instanceof Label label) {
            label.setWrapText(true);
            label.setMaxWidth(contentWidth);
            return;
        }
        if (node instanceof ImageView view) {
            // مع preserveRatio يصير الحدّان معاً إطاراً تدخل الصورة فيه بلا تشويه
            view.setPreserveRatio(true);
            view.setFitWidth(contentWidth);
            return;
        }
        if (node instanceof Region region) {
            region.setMaxWidth(contentWidth);
        }
        if (node instanceof Pane pane) {
            pane.getChildren().forEach(child -> fitToWidth(child, contentWidth));
        } else if (node instanceof Group group) {
            group.getChildren().forEach(child -> fitToWidth(child, contentWidth));
        }
    }

    private static VBox newPage(PrintDocument document, double width, double padding) {
        VBox page = new VBox(BLOCK_SPACING);
        page.setPadding(new Insets(padding));
        page.setStyle("-fx-background-color: white;");
        page.setMinWidth(width);
        page.setPrefWidth(width);
        page.setMaxWidth(width);

        if (document.headerFactory() != null) {
            page.getChildren().add(document.headerFactory().get());
        }
        return page;
    }

    private static Label pageNumber(int page, int total) {
        Label label = new Label(I18n.format("print.pageNumber", page, total));
        label.setFont(Font.font("System", 10));
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /**
     * يضع العقدة في مشهد مؤقت فتُطبَّق عليها الأنماط ويُحسب تخطيطها.
     *
     * <p>حزمة الأنماط تُحمَّل هنا أيضاً لا في المعاينة وحدها: بدونها تُقاس الصفحة بخط
     * افتراضي وتُطبع بخط آخر، فيصير المقاس المحسوب غير مقاس المطبوع.</p>
     *
     * @return الحاوية، ليُفرغها المُستدعي حين ينتهي فتتحرر العقدة
     */
    private static Group layoutOffScreen(Node node) {
        Group holder = new Group(node);
        Scene scene = new Scene(holder);
        URL stylesheet = Printing.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        holder.applyCss();
        holder.layout();
        return holder;
    }

    private static double heightOf(Node node) {
        return node.getLayoutBounds().getHeight();
    }

    // ---------------------------------------------------------------- المعاينة

    /**
     * نافذة معاينة تعرض الصفحات كما ستخرج من الطابعة، واحدة تحت الأخرى.
     *
     * <p>الصفحات مثبَّتة على اتجاه يسار-يمين: الطباعة تجري على عقدة خارج أي مشهد، أي
     * بالاتجاه الافتراضي، فلو ورث محتوى المعاينة اتجاه الواجهة العربي لظهرت المعاينة معكوسة
     * عمّا يخرج من الطابعة - وهو بالضبط ما تُفترض المعاينة أن تمنعه. الشريط حول الورق وحده
     * يتبع لغة الواجهة لأنه جزء من البرنامج لا من المطبوعة.</p>
     */
    private static void showPreview(PrintDocument document, Window owner) {
        Printer printer = requirePrinter(document.kind());
        PageLayout layout = pageLayoutFor(printer, document.kind());
        List<Node> pages = paginate(document, layout);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.get("print.previewTitle"));

        VBox sheets = new VBox(18);
        sheets.setAlignment(Pos.TOP_CENTER);
        sheets.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        sheets.setStyle("-fx-padding: 16; -fx-background-color: #d9dee5;");
        for (Node page : pages) {
            // إطار رمادي حول كل ورقة ليُرى أين تنتهي الصفحة وأين تبدأ التالية
            Group sheet = new Group(page);
            VBox framed = new VBox(sheet);
            framed.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, #00000033, 8, 0, 0, 2);");
            framed.setMaxWidth(Region.USE_PREF_SIZE);
            sheets.getChildren().add(framed);
        }

        ScrollPane scroll = new ScrollPane(sheets);
        scroll.setFitToWidth(true);

        Label target = new Label(I18n.format("print.previewTarget", describeTarget(document.kind())));
        target.getStyleClass().add("muted-text");
        Label count = new Label(I18n.format("print.previewPages", pages.size()));
        count.getStyleClass().add("muted-text");

        Button print = new Button(I18n.get("common.print"));
        print.getStyleClass().add("btn-success");
        Button options = new Button(I18n.get("print.previewOptions"));
        options.getStyleClass().add("btn-secondary");
        Button close = new Button(I18n.get("common.close"));
        close.getStyleClass().add("btn-secondary");

        PageLayout previewLayout = layout;
        print.setOnAction(event -> {
            if (guarded(() -> printBuiltPages(pages, previewLayout, document.kind()))) {
                stage.close();
            }
        });
        // نافذة الطابعة تُعرض داخل sendToPrinter قبل إعادة التقسيم، فالإلغاء فيها
        // يترك صفحات المعاينة كما هي ولا ينتزع منها القطع
        options.setOnAction(event -> {
            if (guarded(() -> sendToPrinter(document, stage, true))) {
                stage.close();
            }
        });
        close.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(12, print, options, spacer, close);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setStyle("-fx-padding: 12 18 12 18;");

        HBox header = new HBox(18, target, count);
        header.setStyle("-fx-padding: 12 18 0 18;");

        // لا UiScale هنا - وليس سهواً: المعاينة تعرض نفس العقد التي تذهب إلى الطابعة،
        // فتكبير خط الجذر يكبّر معها محتوى الورقة ويُظهر صفحةً غير التي ستخرج.
        // نفس سبب تثبيت اتجاه الأوراق على اليسار-لليمين أسفل هذا الملف.
        Scene scene = new Scene(new BorderPane(scroll, header, null, actions, null),
                PREVIEW_WIDTH, PREVIEW_HEIGHT);
        scene.setNodeOrientation(ViewLoader.orientation());
        URL stylesheet = Printing.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.showAndWait();
    }

    /** @return هل تمت العملية بلا خطأ (الفشل يُعرض ولا يُغلق نافذة المعاينة) */
    private static boolean guarded(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
            return false;
        }
    }

    // ---------------------------------------------------------------- الإعدادات

    /** الطابعة والورق المستعملان لهذا النوع، كما يُعرضان في المعاينة وشاشة الإعدادات */
    public static String describeTarget(DocumentKind kind) {
        Printer printer = PrintPreferences.resolvePrinter(kind);
        if (printer == null) {
            return I18n.get("print.noPrinter");
        }
        Paper paper = PrintPreferences.resolvePaper(kind, printer);
        return paper == null
                ? printer.getName()
                : I18n.format("print.targetSummary", printer.getName(), paper.getName());
    }

    /**
     * صفحة تجريبية للتحقق من أن الاختيار في الإعدادات يصل إلى الورق فعلاً.
     * سؤال "هل هذه هي الطابعة الصحيحة بالمقاس الصحيح؟" لا يُجاب عنه إلا بورقة خارجة منها.
     */
    public static void printTestPage(DocumentKind kind, Window owner) {
        Label title = new Label(I18n.get("print.testPageTitle"));
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label body = new Label(I18n.format("print.testPageBody",
                kind.getDisplayName(), describeTarget(kind),
                LocalDateTime.now().format(TEST_PAGE_STAMP)));
        body.setWrapText(true);

        PrintDocument document = kind == DocumentKind.RECEIPT
                ? PrintDocument.receipt()
                : PrintDocument.report();
        print(document.add(title, new Separator(), body), owner);
    }
}
