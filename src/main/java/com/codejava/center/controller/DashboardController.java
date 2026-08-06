package com.codejava.center.controller;

import com.codejava.center.domain.Alert;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.AuthService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.alert.AlertBatch;
import com.codejava.center.service.alert.AlertFeed;
import com.codejava.center.service.alert.AlertService;
import com.codejava.center.service.dto.DailyAttendance;
import com.codejava.center.service.dto.GroupRevenue;
import com.codejava.center.util.AlertPreferences;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.LanguageSelector;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.Toasts;
import com.codejava.center.util.TrayNotifier;
import com.codejava.center.util.UiScale;
import com.codejava.center.util.UiScaleSelector;
import com.codejava.center.util.UserSession;
import com.codejava.center.util.ViewLoader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class DashboardController {

    // حقن الخدمات المطلوبة لجلب الإحصائيات
    private final StudentService studentService;
    private final TransactionService transactionService;
    private final SessionService sessionService;
    private final AttendanceService attendanceService;
    private final UserSession userSession;
    private final SettingsService settingsService;
    private final AuthService authService;
    private final ViewLoader viewLoader;
    private final AlertService alertService;
    private final AlertFeed alertFeed;
    private final TrayNotifier trayNotifier;

    private static final String ACTIVE_STYLE_CLASS = "sidebar-btn-active";
    private static final String BELL_ALERT_STYLE_CLASS = "bell-btn-alert";

    /** فوق هذا العدد تُلخَّص الدفعة في بطاقة واحدة بدل بطاقة لكل تنبيه */
    private static final int INDIVIDUAL_TOAST_LIMIT = 3;

    /** ما تعرضه قائمة الجرس؛ الباقي في صندوق التنبيهات وهو المكان الذي يُقرأ فيه */
    private static final int DROPDOWN_ROWS = 8;

    private static final DateTimeFormatter BELL_TIMESTAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private Popup dropdown;
    private long openAlertCount;
    @FXML
    private StackPane contentArea;
    @FXML
    private Button cashierButton;
    @FXML
    private Button groupsButton;
    @FXML
    private Button teachersButton;
    @FXML
    private Button paymentHistoryButton;
    @FXML
    private Button usersManagementButton;
    @FXML
    private Button auditButton;
    @FXML
    private Button settingsButton;
    @FXML
    private Button shiftClosingButton;
    @FXML
    private Button expensesButton;
    @FXML
    private Button teacherPayoutButton;
    @FXML
    private Button arrearsButton;
    @FXML
    private Button notificationsButton;
    @FXML
    private Button alertsButton;
    @FXML
    private Button bellButton;
    @FXML
    private Button homeButton;
    @FXML
    private Button studentsButton;
    @FXML
    private Button attendanceButton;
    @FXML
    private Button sessionsButton;
    @FXML
    private Button dayScheduleButton;
    @FXML
    private Button attendanceReportButton;
    @FXML
    private Button attendanceLogButton;
    @FXML
    private VBox navDailyBox;
    @FXML
    private VBox navFinanceBox;
    @FXML
    private VBox navReportsBox;
    @FXML
    private VBox navAdminBox;
    @FXML
    private VBox revenueCard;
    @FXML
    private VBox revenueChartCard;
    @FXML
    private Label centerNameLabel;
    @FXML
    private ImageView centerLogoView;
    @FXML
    private Circle logoPlaceholder;
    @FXML
    private Label userRoleLabel;
    @FXML
    private ScrollPane homeScroll;
    @FXML
    private VBox homeView;
    @FXML
    private Label totalStudentsLabel;
    @FXML
    private Label dailyRevenueLabel;
    @FXML
    private Label activeSessionsLabel;
    @FXML
    private Label userNameLabel;
    @FXML
    private ComboBox<Locale> languageCombo;
    @FXML
    private ComboBox<Double> fontScaleCombo;
    @FXML private PieChart revenuePieChart;
    @FXML private BarChart<String, Number> attendanceBarChart;

    @FXML
    public void initialize() {
        User currentUser = userSession.getCurrentUser();

        if (currentUser != null) {
            userNameLabel.setText(currentUser.getUsername());
            userRoleLabel.setText(I18n.format("home.userRole", currentUser.getRole().getDisplayName()));
        }

        // تبديل اللغة يعيد بناء لوحة القيادة بالكامل؛ الجلسة محفوظة في UserSession
        // فلا يُطالَب المستخدم بتسجيل الدخول من جديد
        LanguageSelector.configure(languageCombo, this::reloadDashboard);

        // حجم الخط لا يعيد البناء: سطر تنسيق على الجذر يسري على الشاشة المعروضة نفسها
        UiScaleSelector.configure(fontScaleCombo);

        applyRolePermissions(currentUser);
        loadCenterBranding();

        loadDashboardStats();
        loadChartsData();
        startAlertFeed();
    }

    /**
     * إخفاء ما لا يخص صلاحية المستخدم.
     * الإخفاء للعرض فقط؛ الفرض الحقيقي في طبقة الخدمات عبر @RequiresRole،
     * والغرض منه تفادي فتح شاشة لا تنتج للمستخدم إلا رسالة رفض.
     */
    private void applyRolePermissions(User currentUser) {
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;

        if (!isAdmin) {
            for (Button restricted : new Button[]{
                    cashierButton, groupsButton, teachersButton, usersManagementButton, settingsButton,
                    shiftClosingButton, expensesButton, teacherPayoutButton, arrearsButton, notificationsButton,
                    alertsButton, auditButton}) {
                hide(restricted);
            }
            // بطاقة صافي الدرج ومخطط الإيرادات بيانات مالية أيضاً
            hide(revenueCard);
            hide(revenueChartCard);
        }

        // القسم الذي أُخفيت كل أزراره يترك عنوانه معلّقاً بلا محتوى
        for (VBox section : new VBox[]{navDailyBox, navFinanceBox, navReportsBox, navAdminBox}) {
            hideSectionIfEmpty(section);
        }
    }

    /** setManaged(false) حتى لا يترك العنصر المخفي مساحة فارغة مكانه */
    private void hide(Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    private void hideSectionIfEmpty(VBox section) {
        boolean hasVisibleButton = section.getChildren().stream()
                .anyMatch(child -> child instanceof Button && child.isManaged());

        if (!hasVisibleButton) {
            hide(section);
        }
    }

    /**
     * ترويسة القائمة الجانبية من الإعدادات: اسم السنتر وشعاره بدل نص ثابت ودائرة رمادية.
     * تُقرأ الإعدادات مرة واحدة لأن الاسم والشعار يأتيان من نفس الصف.
     */
    private void loadCenterBranding() {
        FxAsync.supply(settingsService::getSettings, settings -> {
            String name = settings.getCenterName();
            if (name != null && !name.isBlank()) {
                centerNameLabel.setText(name);
            }
            showLogo(settings.getLogoPath());
        }, error -> { /* الاسم الافتراضي المكتوب في FXML والدائرة البديلة يكفيان */ });
    }

    /** الشعار إن وُجد ملفه؛ وإلا تبقى الدائرة الرمادية كما هي */
    private void showLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank() || !new File(logoPath).exists()) {
            return;
        }

        try {
            centerLogoView.setImage(new Image(new File(logoPath).toURI().toString()));
            centerLogoView.setVisible(true);
            centerLogoView.setManaged(true);
            hide(logoPlaceholder);
        } catch (RuntimeException e) {
            // ملف تالف أو صيغة غير مدعومة: الدائرة البديلة أهون من ترويسة فارغة
            e.printStackTrace();
        }
    }


    @FXML
    public void showStudentRegistration(ActionEvent event) {
        loadView("/fxml/StudentRegistration.fxml", studentsButton);
    }

    @FXML
    public void showAttendance(ActionEvent event) {
        loadView("/fxml/AttendanceScreen.fxml", attendanceButton);
    }

    @FXML
    public void showCashier(ActionEvent event) {
        loadView("/fxml/CashierScreen.fxml", cashierButton);
    }

    @FXML
    public void showSessionManagement(ActionEvent event) {
        loadView("/fxml/SessionManagement.fxml", sessionsButton);
    }

    @FXML
    public void showDaySchedule(ActionEvent event) {
        loadView("/fxml/DaySchedule.fxml", dayScheduleButton);
    }

    @FXML
    public void showTeachers(ActionEvent event) {
        loadView("/fxml/TeacherManagement.fxml", teachersButton);
    }

    @FXML
    public void showPaymentHistory(ActionEvent event) {
        loadView("/fxml/PaymentHistory.fxml", paymentHistoryButton);
    }

    @FXML
    public void showGroups(ActionEvent actionEvent) {
        loadView("/fxml/GroupManagement.fxml", groupsButton);
    }
    @FXML
    public void showUsers(ActionEvent event) {
        loadView("/fxml/UserManagement.fxml", usersManagementButton);
    }

    @FXML
    public void showSettings(ActionEvent event) {
        loadView("/fxml/Settings.fxml", settingsButton);
    }

    @FXML
    public void showAuditLog(ActionEvent event) {
        loadView("/fxml/AuditLog.fxml", auditButton);
    }

    @FXML
    public void showShiftClosing(ActionEvent event) {
        loadView("/fxml/ShiftClosing.fxml", shiftClosingButton);
    }

    @FXML
    public void showExpenses(ActionEvent event) {
        loadView("/fxml/Expenses.fxml", expensesButton);
    }

    @FXML
    public void showTeacherPayout(ActionEvent event) {
        loadView("/fxml/TeacherPayout.fxml", teacherPayoutButton);
    }

    @FXML
    public void showArrears(ActionEvent event) {
        loadView("/fxml/Arrears.fxml", arrearsButton);
    }

    @FXML
    public void showAttendanceReport(ActionEvent event) {
        loadView("/fxml/AttendanceReport.fxml", attendanceReportButton);
    }

    @FXML
    public void showAttendanceLog(ActionEvent event) {
        loadView("/fxml/AttendanceLog.fxml", attendanceLogButton);
    }

    @FXML
    public void showNotifications(ActionEvent event) {
        loadView("/fxml/Notifications.fxml", notificationsButton);
    }

    @FXML
    public void showAlerts(ActionEvent event) {
        dismissDropdown();
        loadView("/fxml/AlertCenter.fxml", alertsButton);
    }

    // ============================================================== التنبيهات الحيّة

    /**
     * يربط لوحة القيادة بمصدر التنبيهات.
     *
     * <p>{@link AlertFeed} هو من يملك المؤقّت لا هذا المتحكّم، وذلك مقصود: المتحكّم
     * {@code PROTOTYPE} ويُعاد بناؤه مع كل تبديل للغة، فمؤقّتٌ بداخله يبقى حيّاً بعد أن
     * تُهجَر نسخته ويظلّ يستعلم عن قاعدة البيانات إلى أن يُغلق البرنامج. المصدر مفرد
     * ({@code singleton}) ولا يعرف إلا مستقبِلاً واحداً، فتسجيل الشاشة الجديدة يُسقط
     * القديمة من نفسه.</p>
     *
     * <p>وللمدير وحده: {@code AlertService} محروس، واستدعاؤه بصلاحية سكرتارية يكتب سطر
     * رفض في سجل المراقبة عند كل فتح للشاشة - وهو ضجيج يُفقد السجل معناه.</p>
     */
    private void startAlertFeed() {
        if (!userSession.hasRole(Role.ADMIN)) {
            return;
        }

        bellButton.setVisible(true);
        bellButton.setManaged(true);

        // على خيط خلفي: أول ما يفعله المصدر سؤال قاعدة البيانات عن خط البداية
        FxAsync.run(() -> alertFeed.attach(this::onAlerts), () -> {
        }, error -> { /* الجرس بلا عدّاد أهون من نافذة خطأ فوق شاشة تُفتح توّاً */ });
    }

    /**
     * تسليمة من مصدر التنبيهات، على خيط الواجهة.
     *
     * <p>العدّاد يُحدَّث دائماً، والبطاقات لا تظهر إلا لما يستحق: {@code AlertPreferences}
     * تحدّد أدنى درجة تقفز أمام المستخدم على <b>هذا الجهاز</b>. تأكيد استلام دفعة يقع
     * عشرات المرات في اليوم، وبطاقة لكلٍّ منها تعلّم المستخدم إغلاق البطاقات دون
     * قراءتها - فيغلق الحرجة معها.</p>
     */
    private void onAlerts(AlertBatch batch) {
        showAlertCount(batch.openCount());

        List<Alert> announce = batch.fresh().stream()
                .filter(alert -> AlertPreferences.shouldAnnounce(alert.getSeverity()))
                .toList();

        if (announce.isEmpty()) {
            return;
        }

        // دفعة كبيرة تُلخَّص في بطاقة واحدة: خمس عشرة بطاقة متتابعة - وخمس عشرة فقاعة
        // في شريط المهام خلفها - تحجب الشاشة، ولا تُقرأ منها واحدة
        if (announce.size() > INDIVIDUAL_TOAST_LIMIT) {
            announceSummary(announce);
        } else {
            announce.forEach(this::announceOne);
        }
    }

    private void announceOne(Alert alert) {
        String title = alert.getType().getDisplayName();
        String message = alert.describe();

        if (AlertPreferences.popupEnabled()) {
            Toasts.show(window(), title, message, alert.getSeverity().getAccentColor(),
                    () -> showAlerts(null));
        }
        trayNotifier.notifyUser(title, message, alert.getSeverity() == AlertSeverity.CRITICAL);
    }

    private void announceSummary(List<Alert> announce) {
        boolean anyCritical = announce.stream()
                .anyMatch(alert -> alert.getSeverity() == AlertSeverity.CRITICAL);

        String title = I18n.get("alerts.toastSummaryTitle");
        String message = I18n.format("alerts.toastSummary", announce.size());

        if (AlertPreferences.popupEnabled()) {
            Toasts.show(window(), title, message,
                    (anyCritical ? AlertSeverity.CRITICAL : AlertSeverity.WARNING).getAccentColor(),
                    () -> showAlerts(null));
        }
        trayNotifier.notifyUser(title, message, anyCritical);
    }

    private void showAlertCount(long openCount) {
        openAlertCount = openCount;
        bellButton.setText(openCount == 0
                ? I18n.get("alerts.bell") : I18n.format("alerts.bellWithCount", openCount));

        // الصفر لا يُلوَّن: جرسٌ أحمر دائماً يصير جزءاً من خلفية الشاشة
        bellButton.getStyleClass().remove(BELL_ALERT_STYLE_CLASS);
        if (openCount > 0) {
            bellButton.getStyleClass().add(BELL_ALERT_STYLE_CLASS);
        }
    }

    /**
     * قائمة الجرس: آخر ما لم يُعالَج، بلا مغادرة الشاشة الحالية.
     *
     * <p>{@link Popup} لا نافذة: نافذةٌ مستقلة تسرق التركيز من الحقل الذي يكتب فيه
     * الموظف. و{@code autoHide} يُغلقها بأول ضغطة خارجها، وهو ما يتوقعه من فتح قائمة
     * منسدلة.</p>
     */
    @FXML
    public void showAlertDropdown(ActionEvent event) {
        if (dropdown != null && dropdown.isShowing()) {
            dismissDropdown();
            return;
        }

        FxAsync.supply(() -> alertService.search(LocalDate.now().minusWeeks(2), LocalDate.now(), null, null),
                page -> showDropdown(page.rows().stream().filter(alert -> !alert.isAcknowledged())
                        .limit(DROPDOWN_ROWS).toList()),
                error -> Dialogs.error(I18n.format("alerts.loadFailed", FxAsync.messageOf(error))));
    }

    private void showDropdown(List<Alert> open) {
        dismissDropdown();

        VBox rows = new VBox(2);
        if (open.isEmpty()) {
            Label empty = new Label(I18n.get("alerts.bellEmpty"));
            empty.setStyle("-fx-text-fill: #7f8c8d; -fx-padding: 1.29em;");
            rows.getChildren().add(empty);
        } else {
            open.forEach(alert -> rows.getChildren().add(dropdownRow(alert)));
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(UiScale.scaled(Math.min(320, 74.0 * Math.max(1, open.size()) + 12)));
        // -fx-background-color وحدها، ولا يُضاف إليها -fx-background أبداً: الثانية
        // اللونُ المرجعيّ الذي تبني عليه modena لونَ النصّ عبر ladder، و transparent
        // يُقرأ سطوعه صفراً فيصير نصّ كل ما تحتها أبيض على أبيض. راجع .content-scroll
        scroll.setStyle("-fx-background-color: transparent;");
        scroll.getStyleClass().add("content-scroll");

        Button openCentre = new Button(I18n.get("alerts.bellOpenCentre"));
        openCentre.setMaxWidth(Double.MAX_VALUE);
        openCentre.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-padding: 0.57em 1.14em;");
        openCentre.setOnAction(e -> showAlerts(null));

        Label header = new Label(I18n.format("alerts.bellTitle", openAlertCount));
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 1em; -fx-padding: 0.86em 1em 0.57em 1em;");

        VBox content = new VBox(8, header, new Separator(), scroll, openCentre);
        content.setPrefWidth(UiScale.scaled(380));
        content.setPadding(new Insets(0, 10, 10, 10));
        // حجم الخط على الحاوية: القائمة المنسدلة Popup بمشهد مستقلّ لا يصله سطر الجذر،
        // فترث منها الأسطر الداخلية مقاسها وتعمل الـ em المكتوبة فيها
        content.setStyle("-fx-background-color: white; -fx-background-radius: 8;"
                + " -fx-border-color: #dfe4ea; -fx-border-radius: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 14, 0, 0, 4);"
                + UiScale.fontSizeStyle());
        // الـ Popup ليس ابناً للمشهد فلا يرث اتجاهه
        content.setNodeOrientation(ViewLoader.orientation());

        dropdown = new Popup();
        dropdown.setAutoHide(true);
        dropdown.setHideOnEscape(true);
        dropdown.getContent().add(content);

        javafx.geometry.Bounds bell = bellButton.localToScreen(bellButton.getBoundsInLocal());
        dropdown.show(bellButton, bell.getMinX(), bell.getMaxY() + 6);
    }

    private Node dropdownRow(Alert alert) {
        Region dot = new Region();
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.setStyle("-fx-background-color: " + alert.getSeverity().getAccentColor()
                + "; -fx-background-radius: 4;");

        Label message = new Label(alert.describe());
        message.setWrapText(true);
        message.setStyle("-fx-font-size: 0.86em; -fx-text-fill: #2c3e50;");

        Label when = new Label(alert.getRaisedAt().format(BELL_TIMESTAMP));
        when.setStyle("-fx-font-size: 0.79em; -fx-text-fill: #95a5a6;");

        VBox text = new VBox(3, message, when);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(8, dot, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(8, 6, 8, 6));
        return row;
    }

    private void dismissDropdown() {
        if (dropdown != null) {
            dropdown.hide();
            dropdown = null;
        }
    }

    /** نافذة الشاشة الحالية، أو {@code null} قبل عرض المشهد */
    private Window window() {
        return bellButton.getScene() == null ? null : bellButton.getScene().getWindow();
    }

    /**
     * فكّ الارتباط قبل هجر هذه النسخة من المتحكّم.
     *
     * <p>يُستدعى عند تسجيل الخروج وعند تبديل اللغة. البطاقات المعلّقة تُخفى معه: هي
     * معلّقة على نافذة يُعاد بناء مشهدها، وبطاقة عن أرصدة الطلاب تطفو فوق شاشة الدخول
     * تعرض بيانات على من لم يسجّل دخوله بعد.</p>
     */
    private void stopAlertFeed() {
        alertFeed.detach();
        dismissDropdown();
        Toasts.hideAll();
    }
    private void loadDashboardStats() {
        // البيانات المالية متاحة للمدير فقط؛ استدعاؤها بصلاحية سكرتارية
        // سيرمي AccessDeniedException من طبقة الخدمات
        boolean isAdmin = userSession.hasRole(Role.ADMIN);

        CompletableFuture.supplyAsync(() -> {
            // عدٌّ في القاعدة: قراءة كل الطلاب ليُقاس طول القائمة كانت تجلب الجدول
            // كاملاً - بمن فيهم المؤرشفون - في كل مرة تُفتح فيها اللوحة
            long studentsCount = studentService.countActiveStudents();
            BigDecimal revenue = isAdmin ? transactionService.calculateTodayNetBalance() : null;
            long activeSessions = sessionService.getActiveSessions().size();

            return new DashboardStats(studentsCount, revenue, activeSessions);
        }).thenAccept(stats -> Platform.runLater(() -> {
            totalStudentsLabel.setText(String.valueOf(stats.studentsCount));
            dailyRevenueLabel.setText(stats.dailyRevenue == null
                    ? I18n.get("common.empty") : MoneyUtils.formatWithCurrency(stats.dailyRevenue));
            activeSessionsLabel.setText(String.valueOf(stats.activeSessions));
        })).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    @FXML
    public void showHome(ActionEvent event) {
        // homeScroll هو الغلاف القابل للتمرير حول homeView؛ إعادة homeView وحده
        // تُفقد التمرير فتُقصّ المخططات على الشاشات القصيرة
        contentArea.getChildren().setAll(homeScroll);
        markActive(homeButton);

        loadDashboardStats(); // تحديث الأرقام عند العودة للرئيسية
        loadChartsData();
    }

    private void loadView(String fxmlPath, Button sourceButton) {
        try {
            Node view = viewLoader.load(fxmlPath);
            contentArea.getChildren().setAll(view);
            markActive(sourceButton);
        } catch (IOException | RuntimeException e) {
            // الفشل الصامت كان يترك الشاشة كما هي فيظن المستخدم أن الزر لا يعمل
            e.printStackTrace();
            Dialogs.error(I18n.format("home.openFailed", FxAsync.messageOf(e)));
        }
    }

    /**
     * إبراز الزر المفتوح حالياً.
     * كان التمييز مكتوباً ثابتاً على "الرئيسية" في FXML ولا ينتقل أبداً،
     * فيبقى يشير إلى الرئيسية مهما تنقّل المستخدم.
     */
    private void markActive(Button activeButton) {
        for (VBox section : new VBox[]{navDailyBox, navFinanceBox, navReportsBox, navAdminBox}) {
            section.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .forEach(button -> button.getStyleClass().remove(ACTIVE_STYLE_CLASS));
        }

        if (activeButton != null && !activeButton.getStyleClass().contains(ACTIVE_STYLE_CLASS)) {
            activeButton.getStyleClass().add(ACTIVE_STYLE_CLASS);
        }
    }

    private void loadChartsData() {
        // مخطط الإيرادات مالي: يُخفى عن السكرتارية بدل استدعاء دالة محظورة عليها
        boolean isAdmin = userSession.hasRole(Role.ADMIN);
        revenuePieChart.setVisible(isAdmin);
        revenuePieChart.setManaged(isAdmin);

        CompletableFuture.supplyAsync(() -> new ChartsData(
                isAdmin ? transactionService.getRevenueByGroupLast30Days() : List.of(),
                attendanceService.getAttendanceLast7Days()
        )).thenAccept(data -> Platform.runLater(() -> {
            // 1. مخطط الإيرادات حسب المجموعة (آخر 30 يوماً)
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (GroupRevenue row : data.revenue()) {
                BigDecimal value = MoneyUtils.normalize(row.total());
                grandTotal = grandTotal.add(value);
                pieChartData.add(new PieChart.Data(row.groupName(), value.doubleValue()));
            }
            revenuePieChart.setData(pieChartData);

            // النسب تُحسب من الإجمالي الفعلي وليس من رقم ثابت
            final BigDecimal total = grandTotal;
            revenuePieChart.getData().forEach(slice -> {
                String percentage = total.signum() == 0
                        ? "0.0%"
                        : String.format("%.1f%%", (slice.getPieValue() / total.doubleValue()) * 100);
                Tooltip.install(slice.getNode(),
                        new Tooltip(slice.getName() + ": " + MoneyUtils.formatWithCurrency(BigDecimal.valueOf(slice.getPieValue()))
                                + " (" + percentage + ")"));
            });

            // 2. مخطط الحضور خلال آخر 7 أيام - تُعرض كل الأيام حتى الأيام بلا حضور
            Map<LocalDate, Long> countsByDate = data.attendance().stream()
                    .collect(Collectors.toMap(DailyAttendance::date, DailyAttendance::count));

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(I18n.get("home.series.attendance"));
            // أسماء الأيام بلغة الواجهة الحالية، لا بلغة ثابتة
            DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("EEEE", I18n.current());
            for (int i = 6; i >= 0; i--) {
                LocalDate day = LocalDate.now().minusDays(i);
                series.getData().add(new XYChart.Data<>(
                        day.format(dayLabel), countsByDate.getOrDefault(day, 0L)));
            }

            attendanceBarChart.getData().clear();
            attendanceBarChart.getData().add(series);
        })).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    // حاوية لنقل بيانات المخططات بين الـ Threads
    private record ChartsData(List<GroupRevenue> revenue, List<DailyAttendance> attendance) {
    }

    private void reloadDashboard() {
        try {
            // قبل إعادة البناء لا بعده: النسخة الجديدة تسجّل نفسها في المصدر فوراً،
            // وفكّ الارتباط بعدها كان سيُسقط تسجيلها هي
            stopAlertFeed();
            viewLoader.showDashboard(stageOf(languageCombo));
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(FxAsync.messageOf(e));
        }
    }

    public void handleLogout(ActionEvent actionEvent) {
        try {
            // 1. إنهاء الجلسة الحالية أولاً حتى لا يرث المستخدم التالي صلاحيات السابق
            User leaving = userSession.getCurrentUser();
            userSession.cleanUserSession();
            stopAlertFeed();

            // 2. تسجيل الخروج في سجل المراقبة في الخلفية: بداية الجلسة ونهايتها هما ما
            // يحدّد أي أحداث تقع في نطاق مسؤولية من، وخيط الواجهة لا ينتظر قاعدة البيانات
            FxAsync.run(() -> authService.recordLogout(leaving), () -> {
            }, Throwable::printStackTrace);

            // 3. العودة إلى شاشة الدخول
            viewLoader.showLogin(stageOf(actionEvent.getSource()));
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(I18n.format("home.logoutFailed", FxAsync.messageOf(e)));
        }
    }

    private Stage stageOf(Object node) {
        return (Stage) ((Node) node).getScene().getWindow();
    }

    // كلاس داخلي لنقل بيانات الإحصائيات
    private record DashboardStats(long studentsCount, BigDecimal dailyRevenue, long activeSessions) {
    }
}
