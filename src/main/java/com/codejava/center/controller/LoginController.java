package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.service.AuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;
    private final ApplicationContext applicationContext;

    @Value("classpath:/fxml/Dashboard.fxml")
    private Resource dashboardFxml;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("يرجى إدخال اسم المستخدم وكلمة المرور.");
            return;
        }

        errorLabel.setVisible(false);

        // تنفيذ التحقق في Thread منفصل لعدم تجميد واجهة المستخدم
        CompletableFuture.supplyAsync(() -> authService.authenticate(username, password))
                .thenAccept(user -> Platform.runLater(() -> loadDashboard(event, user)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showError(ex.getCause().getMessage()));
                    return null;
                });
    }

    private void loadDashboard(ActionEvent event, User user) {
        try {


            // حفظ بيانات المستخدم في الجلسة
            com.codejava.center.util.UserSession.setCurrentUser(user);

            FXMLLoader fxmlLoader = new FXMLLoader(dashboardFxml.getURL());
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();

            // الحصول على الـ Stage الحالية واستبدال الـ Scene
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1024, 768);
             // إضافة ملف التصميم ليطبق على لوحة القيادة وجميع الشاشات الفرعية داخلها
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();

            // يمكن هنا حفظ بيانات المستخدم الجلسة (Session) لاستخدامها في باقي النظام
            // SessionManager.setCurrentUser(user);

        } catch (IOException e) {
            showError("حدث خطأ أثناء تحميل لوحة القيادة.");
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}