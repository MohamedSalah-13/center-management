package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.DailyAttendance;
import com.codejava.center.service.dto.GroupRevenue;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
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

    // حقن Spring Context لإدارة متحكمات الشاشات الفرعية
    private final ApplicationContext applicationContext;
    // حقن الخدمات المطلوبة لجلب الإحصائيات
    private final StudentService studentService;
    private final TransactionService transactionService;
    private final SessionService sessionService;
    private final AttendanceService attendanceService;
    private final UserSession userSession;
    private final SettingsService settingsService;

    private static final Locale ARABIC_LOCALE = Locale.forLanguageTag("ar");
    private static final String ACTIVE_STYLE_CLASS = "sidebar-btn-active";
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
    private Button homeButton;
    @FXML
    private Button studentsButton;
    @FXML
    private Button attendanceButton;
    @FXML
    private Button sessionsButton;
    @FXML
    private Button attendanceReportButton;
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
    @FXML private PieChart revenuePieChart;
    @FXML private BarChart<String, Number> attendanceBarChart;

    @FXML
    public void initialize() {
        User currentUser = userSession.getCurrentUser();

        if (currentUser != null) {
            userNameLabel.setText(currentUser.getUsername());
            userRoleLabel.setText("(" + currentUser.getRole().getArabicName() + ")");
        }

        applyRolePermissions(currentUser);
        loadCenterName();

        loadDashboardStats();
        loadChartsData();
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
                    shiftClosingButton, expensesButton, teacherPayoutButton, arrearsButton, notificationsButton}) {
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

    /** اسم السنتر من الإعدادات بدل نص ثابت في الواجهة */
    private void loadCenterName() {
        FxAsync.supply(settingsService::getCenterName,
                name -> centerNameLabel.setText(name),
                error -> { /* الاسم الافتراضي المكتوب في FXML يكفي */ });
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
    public void showNotifications(ActionEvent event) {
        loadView("/fxml/Notifications.fxml", notificationsButton);
    }
    private void loadDashboardStats() {
        // البيانات المالية متاحة للمدير فقط؛ استدعاؤها بصلاحية سكرتارية
        // سيرمي AccessDeniedException من طبقة الخدمات
        boolean isAdmin = userSession.hasRole(Role.ADMIN);

        CompletableFuture.supplyAsync(() -> {
            long studentsCount = studentService.getAllStudents().size(); // يفضل عمل دالة count() في الـ Repository
            BigDecimal revenue = isAdmin ? transactionService.calculateTodayNetBalance() : null;
            long activeSessions = sessionService.getActiveSessions().size();

            return new DashboardStats(studentsCount, revenue, activeSessions);
        }).thenAccept(stats -> Platform.runLater(() -> {
            totalStudentsLabel.setText(String.valueOf(stats.studentsCount));
            dailyRevenueLabel.setText(stats.dailyRevenue == null
                    ? "—" : MoneyUtils.formatWithCurrency(stats.dailyRevenue));
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
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                throw new IllegalArgumentException("الملف غير موجود: " + fxmlPath);
            }
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
            markActive(sourceButton);
        } catch (IOException e) {
            // الفشل الصامت كان يترك الشاشة كما هي فيظن المستخدم أن الزر لا يعمل
            e.printStackTrace();
            showError("تعذر فتح الشاشة: " + e.getMessage());
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
            series.setName("الحضور الفعلي");
            DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("EEEE", ARABIC_LOCALE);
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
    public void handleLogout(ActionEvent actionEvent) {
        try {
            // 1. إنهاء الجلسة الحالية أولاً حتى لا يرث المستخدم التالي صلاحيات السابق
            userSession.cleanUserSession();

            // 2. العودة إلى شاشة الدخول
            URL resource = getClass().getResource("/fxml/Login.fxml");
            if (resource == null) {
                throw new IllegalStateException("الملف غير موجود: /fxml/Login.fxml");
            }
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(applicationContext::getBean);
            Parent loginRoot = loader.load();

            Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            Scene scene = new Scene(loginRoot, 500, 400);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            // رفع قيود نافذة لوحة القيادة قبل عرض شاشة الدخول الصغيرة،
            // وإلا بقيت النافذة مكبّرة وبحد أدنى 1100×700 حول واجهة 500×400
            stage.setMaximized(false);
            stage.setMinWidth(0);
            stage.setMinHeight(0);

            stage.setScene(scene);
            stage.setTitle("تسجيل الدخول - نظام إدارة السنتر");
            stage.setWidth(500);
            stage.setHeight(400);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            showError("تعذر العودة إلى شاشة الدخول: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("خطأ");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // كلاس داخلي لنقل بيانات الإحصائيات
    private record DashboardStats(long studentsCount, BigDecimal dailyRevenue, long activeSessions) {
    }
}