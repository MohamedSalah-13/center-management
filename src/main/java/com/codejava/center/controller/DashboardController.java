package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    // حقن Spring Context لإدارة متحكمات الشاشات الفرعية
    private final ApplicationContext applicationContext;
    // حقن الخدمات المطلوبة لجلب الإحصائيات
    private final StudentService studentService;
    private final TransactionService transactionService;
    private final SessionService sessionService;
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
        User currentUser = UserSession.getCurrentUser();

        // تطبيق الصلاحيات
        if (currentUser != null && "SECRETARY".equalsIgnoreCase(currentUser.getRole())) {
            userNameLabel.setText(currentUser.getUsername());
            // إخفاء زر الخزينة
            cashierButton.setVisible(false);
            cashierButton.setManaged(false); // setManaged(false) تجعل الزر لا يأخذ مساحة فارغة في القائمة

            // إخفاء زر المجموعات
            groupsButton.setVisible(false);
            groupsButton.setManaged(false);

            // 2. إخفاء زر المعلمين عن السكرتارية (لأنه يحتوي على بيانات اللائحة المالية للرواتب)
            teachersButton.setVisible(false);
            teachersButton.setManaged(false);
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

    private void loadDashboardStats() {
        // تنفيذ جلب البيانات في Thread منفصل لضمان سلاسة الواجهة
        CompletableFuture.supplyAsync(() -> {
            long studentsCount = studentService.getAllStudents().size(); // يفضل عمل دالة count() في الـ Repository
            double revenue = transactionService.calculateTodayNetBalance();
            long activeSessions = sessionService.getAllSessions().stream().filter(s -> s.isActive()).count();

            return new DashboardStats(studentsCount, revenue, activeSessions);
        }).thenAccept(stats -> Platform.runLater(() -> {
            totalStudentsLabel.setText(String.valueOf(stats.studentsCount));
            dailyRevenueLabel.setText(stats.dailyRevenue + " ج.م");
            activeSessionsLabel.setText(String.valueOf(stats.activeSessions));
        }));
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
        CompletableFuture.runAsync(() -> {
            // 1. جلب أو تجهيز بيانات المخطط الدائري (PieChart)
            // في النظام الفعلي: ستقوم بعمل Query لجلب إجمالي الإيرادات مجمعة حسب الـ Group
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                    new PieChart.Data("مجموعة أ. أحمد", 1500),
                    new PieChart.Data("مجموعة أ. محمود", 2200),
                    new PieChart.Data("مجموعة أ. سارة", 1800)
            );

            // 2. جلب أو تجهيز بيانات مخطط الأعمدة (BarChart)
            // في النظام الفعلي: Query لجلب عدد الحضور خلال الأسبوع
            XYChart.Series<String, Number> series1 = new XYChart.Series<>();
            series1.setName("الحضور الفعلي");
            series1.getData().add(new XYChart.Data<>("السبت", 45));
            series1.getData().add(new XYChart.Data<>("الأحد", 60));
            series1.getData().add(new XYChart.Data<>("الإثنين", 35));
            series1.getData().add(new XYChart.Data<>("الثلاثاء", 55));

            // 3. تحديث الواجهة في الـ JavaFX Thread
            Platform.runLater(() -> {
                revenuePieChart.setData(pieChartData);

                attendanceBarChart.getData().clear();
                attendanceBarChart.getData().add(series1);

                // إضافة تأثيرات حركية خفيفة (Animations)
                revenuePieChart.getData().forEach(data -> {
                    String percentage = String.format("%.1f%%", (data.getPieValue() / 5500) * 100);
                    Tooltip toolTip = new Tooltip(percentage);
                    Tooltip.install(data.getNode(), toolTip);
                });
            });
        });
    }
    public void handleLogout(ActionEvent actionEvent) {

    }

    // كلاس داخلي لنقل بيانات الإحصائيات
    private record DashboardStats(long studentsCount, double dailyRevenue, long activeSessions) {
    }
}