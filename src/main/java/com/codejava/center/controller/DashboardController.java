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

    /**
     * الدالة المركزية لتحميل الشاشات ووضعها داخل الداش بورد
     */
    private void loadView(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                throw new IllegalArgumentException("الملف غير موجود: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resource);

            // السطر الأهم: إخبار JavaFX أن Spring هو من سيدير الكلاس المتحكم
            loader.setControllerFactory(applicationContext::getBean);

            Node view = loader.load();

            // تفريغ الشاشة القديمة ووضع الشاشة الجديدة
            contentArea.getChildren().setAll(view);

        } catch (IOException e) {
            e.printStackTrace(); // في بيئة الإنتاج يفضل إظهار Alert للمستخدم
        }
    }

    public void showGroups(ActionEvent actionEvent) {
        loadView("/fxml/GroupManagement.fxml");
    }
}