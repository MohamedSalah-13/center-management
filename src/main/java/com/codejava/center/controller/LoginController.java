package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.service.AuthService;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.LanguageSelector;
import com.codejava.center.util.UiScaleSelector;
import com.codejava.center.util.UserSession;
import com.codejava.center.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.Locale;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;
    private final UserSession userSession;
    private final ViewLoader viewLoader;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private ComboBox<Locale> languageCombo;
    @FXML private ComboBox<Double> fontScaleCombo;

    @FXML
    public void initialize() {
        // تبديل اللغة هنا يعيد بناء شاشة الدخول نفسها، فيرى المشغّل أثر اختياره فوراً
        LanguageSelector.configure(languageCombo, this::reloadLoginScreen);

        // وحجم الخط يسري بلا إعادة بناء، وتتسع النافذة له إن ضاقت
        UiScaleSelector.configure(fontScaleCombo);
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(I18n.get("login.emptyFields"));
            return;
        }

        errorLabel.setVisible(false);

        // التحقق في الخلفية حتى لا تتجمّد الواجهة أثناء انتظار قاعدة البيانات
        FxAsync.supply(() -> authService.authenticate(username, password),
                user -> loadDashboard(event, user),
                error -> showError(FxAsync.messageOf(error)));
    }

    private void loadDashboard(ActionEvent event, User user) {
        try {
            // حفظ بيانات المستخدم في الجلسة قبل بناء لوحة القيادة:
            // متحكّم اللوحة يقرأ الدور فور إنشائه ليخفي ما لا يخص صلاحية المستخدم
            userSession.setCurrentUser(user);

            viewLoader.showDashboard(stageOf(event.getSource()));
        } catch (IOException e) {
            e.printStackTrace();
            showError(I18n.get("login.dashboardFailed"));
        }
    }

    private void reloadLoginScreen() {
        try {
            viewLoader.showLogin(stageOf(languageCombo));
        } catch (IOException e) {
            e.printStackTrace();
            showError(FxAsync.messageOf(e));
        }
    }

    private Stage stageOf(Object node) {
        return (Stage) ((Node) node).getScene().getWindow();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
