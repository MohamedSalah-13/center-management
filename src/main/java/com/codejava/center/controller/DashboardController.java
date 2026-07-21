package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.util.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    // حقن Spring Context لإدارة متحكمات الشاشات الفرعية
    private final ApplicationContext applicationContext;

    @FXML
    private StackPane contentArea;
    // تعريف الأزرار التي أضفنا لها ID
    @FXML
    private Button cashierButton;
    @FXML
    private Button groupsButton;
    @FXML
    private Button teachersButton;

    @FXML
    public void initialize() {
        // جلب المستخدم المسجل حالياً من الجلسة
        User currentUser = UserSession.getCurrentUser();

        // تطبيق الصلاحيات
        if (currentUser != null && "SECRETARY".equalsIgnoreCase(currentUser.getRole())) {
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
    }

    @FXML
    public void showHome(ActionEvent event) {
        // يمكنك لاحقاً تصميم Home.fxml لعرض إحصائيات سريعة
        contentArea.getChildren().clear();
        // مؤقتاً نتركها فارغة للعودة للشكل الافتراضي
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

    public void showGroups(ActionEvent actionEvent) {
        loadView("/fxml/GroupManagement.fxml");
    }
}