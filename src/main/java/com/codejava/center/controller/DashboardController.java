package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.DailyAttendance;
import com.codejava.center.service.dto.GroupRevenue;
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

    private static final Locale ARABIC_LOCALE = Locale.forLanguageTag("ar");
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
        // جلب المستخدم المسجل حالياً من الجلسة
        User currentUser = userSession.getCurrentUser();

        // اسم المستخدم يُعرض لكل الصلاحيات وليس للسكرتارية فقط
        if (currentUser != null) {
            userNameLabel.setText(currentUser.getUsername());
        }

        // تطبيق الصلاحيات - إخفاء الأزرار فقط؛ الفرض الحقيقي في طبقة الخدمات عبر @RequiresRole
        if (currentUser != null && currentUser.getRole() == Role.SECRETARY) {
            // إخفاء زر الخزينة
            cashierButton.setVisible(false);
            cashierButton.setManaged(false); // setManaged(false) تجعل الزر لا يأخذ مساحة فارغة في القائمة

            // إخفاء زر المجموعات
            groupsButton.setVisible(false);
            groupsButton.setManaged(false);

            // 2. إخفاء زر المعلمين عن السكرتارية (لأنه يحتوي على بيانات اللائحة المالية للرواتب)
            teachersButton.setVisible(false);
            teachersButton.setManaged(false);

            usersManagementButton.setVisible(false);
            usersManagementButton.setManaged(false);

            // إخفاء زر الإعدادات عن السكرتارية (متاح للمدير فقط)
            settingsButton.setVisible(false);
            settingsButton.setManaged(false);

            // الشاشات المالية الثلاث: جرد الخزينة والمصروفات وصرف المستحقات
            // خدماتها محمية بـ @RequiresRole(ADMIN)، والإخفاء هنا لتفادي رسالة رفض للمستخدم
            for (Button financialButton : new Button[]{shiftClosingButton, expensesButton, teacherPayoutButton, arrearsButton}) {
                financialButton.setVisible(false);
                financialButton.setManaged(false);
            }
        }

        loadDashboardStats();
        loadChartsData();
    }


    @FXML
    public void showStudentRegistration(ActionEvent event) {
        loadView("/fxml/StudentRegistration.fxml");
    }

    @FXML
    public void showAttendance(ActionEvent event) {
        loadView("/fxml/AttendanceScreen.fxml");
    }

    @FXML
    public void showCashier(ActionEvent event) {
        loadView("/fxml/CashierScreen.fxml");
    }

    @FXML
    public void showSessionManagement(ActionEvent event) {
        loadView("/fxml/SessionManagement.fxml");
    }

    @FXML
    public void showTeachers(ActionEvent event) {
        loadView("/fxml/TeacherManagement.fxml");
    }

    @FXML
    public void showPaymentHistory(ActionEvent event) {
        loadView("/fxml/PaymentHistory.fxml");
    }

    @FXML
    public void showGroups(ActionEvent actionEvent) {
        loadView("/fxml/GroupManagement.fxml");
    }
    @FXML
    public void showUsers(ActionEvent event) {
        loadView("/fxml/UserManagement.fxml");
    }

    @FXML
    public void showSettings(ActionEvent event) {
        loadView("/fxml/Settings.fxml");
    }

    @FXML
    public void showShiftClosing(ActionEvent event) {
        loadView("/fxml/ShiftClosing.fxml");
    }

    @FXML
    public void showExpenses(ActionEvent event) {
        loadView("/fxml/Expenses.fxml");
    }

    @FXML
    public void showTeacherPayout(ActionEvent event) {
        loadView("/fxml/TeacherPayout.fxml");
    }

    @FXML
    public void showArrears(ActionEvent event) {
        loadView("/fxml/Arrears.fxml");
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
        contentArea.getChildren().setAll(homeView);
        loadDashboardStats(); // تحديث الأرقام عند العودة للرئيسية
    }

    private void loadView(String fxmlPath) {
        // نفس الكود الخاص بك دون تغيير
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                throw new IllegalArgumentException("الملف غير موجود: " + fxmlPath);
            }
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(applicationContext::getBean);
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
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
            stage.setScene(scene);
            stage.setTitle("تسجيل الدخول - نظام إدارة السنتر");
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