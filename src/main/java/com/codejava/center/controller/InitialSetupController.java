package com.codejava.center.controller;

import com.codejava.center.service.InitialSetupService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.LanguageSelector;
import com.codejava.center.util.UiScaleSelector;
import com.codejava.center.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.Locale;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class InitialSetupController {

    private final InitialSetupService initialSetupService;
    private final ViewLoader viewLoader;

    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmationField;
    @FXML private Label errorLabel;
    @FXML private Button createButton;
    @FXML private ComboBox<Locale> languageCombo;
    @FXML private ComboBox<Double> fontScaleCombo;

    @FXML
    public void initialize() {
        LanguageSelector.configure(languageCombo, this::reloadSetupScreen);
        UiScaleSelector.configure(fontScaleCombo);
    }

    @FXML
    public void handleCreate(ActionEvent event) {
        String password = passwordField.getText();
        String confirmation = confirmationField.getText();

        setBusy(true);
        errorLabel.setVisible(false);

        // التحقق والحفظ والتشفير أعمال خدمة/قاعدة بيانات، فلا تُنفّذ على خيط JavaFX.
        FxAsync.supply(() -> initialSetupService.createInitialAdmin(password, confirmation),
                ignored -> showLoginAfterCreation(event),
                error -> {
                    setBusy(false);
                    showError(FxAsync.messageOf(error));
                });
    }

    private void showLoginAfterCreation(ActionEvent event) {
        try {
            Dialogs.success(I18n.get("setup.success"));
            viewLoader.showLogin(stageOf(event.getSource()));
        } catch (IOException e) {
            setBusy(false);
            showError(I18n.format("common.loadFailed", FxAsync.messageOf(e)));
        }
    }

    private void reloadSetupScreen() {
        try {
            viewLoader.showInitialSetup(stageOf(languageCombo));
        } catch (IOException e) {
            showError(I18n.format("common.loadFailed", FxAsync.messageOf(e)));
        }
    }

    private void setBusy(boolean busy) {
        passwordField.setDisable(busy);
        confirmationField.setDisable(busy);
        createButton.setDisable(busy);
    }

    private Stage stageOf(Object node) {
        return (Stage) ((Node) node).getScene().getWindow();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
