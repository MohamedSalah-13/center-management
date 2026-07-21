package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.service.UserService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class UserManagementController {

    private final UserService userService;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colId, colUsername, colRole;
    @FXML private Button updateButton, deleteButton;

    private User selectedUser = null;
    private final ObservableList<User> usersList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // تحديد الصلاحيات المتاحة في النظام
        roleCombo.getItems().addAll("ADMIN", "SECRETARY");

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));

        // ترجمة الصلاحيات لتعرض بالعربية في الجدول
        colRole.setCellValueFactory(data -> {
            String role = data.getValue().getRole();
            return new SimpleStringProperty("ADMIN".equals(role) ? "مدير نظام" : "سكرتارية");
        });

        usersTable.setItems(usersList);
        setupTableSelectionListener();
        loadData();
    }

    private void loadData() {
        CompletableFuture.supplyAsync(userService::getAllUsers)
                .thenAccept(users -> Platform.runLater(() -> usersList.setAll(users)));
    }

    private void setupTableSelectionListener() {
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedUser = newVal;
                usernameField.setText(selectedUser.getUsername());
                roleCombo.setValue(selectedUser.getRole());
                passwordField.clear(); // مسح حقل الباسورد لدواعي أمنية

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        saveOrUpdateUser(new User());
    }

    @FXML
    public void handleUpdateAction(ActionEvent event) {
        if (selectedUser != null) {
            saveOrUpdateUser(selectedUser);
        }
    }

    private void saveOrUpdateUser(User user) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String role = roleCombo.getValue();

        if (username.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إدخال اسم المستخدم واختيار الصلاحية.");
            return;
        }

        user.setUsername(username);
        user.setRole(role);

        try {
            User saved = userService.saveUser(user, password);
            if (user.getId() == null) {
                usersList.add(saved);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تمت إضافة المستخدم بنجاح.");
            } else {
                int idx = usersTable.getSelectionModel().getSelectedIndex();
                usersList.set(idx, saved);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم تحديث بيانات المستخدم.");
            }
            clearForm();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedUser == null) return;

        // منع المدير من حذف حسابه الشخصي بالخطأ
        if ("admin".equalsIgnoreCase(selectedUser.getUsername())) {
            showAlert(Alert.AlertType.ERROR, "مرفوض", "لا يمكن حذف الحساب الافتراضي للمدير.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هل أنت متأكد من حذف المستخدم: " + selectedUser.getUsername() + "؟", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                userService.deleteUser(selectedUser.getId());
                usersList.remove(selectedUser);
                clearForm();
            }
        });
    }

    @FXML
    private void clearForm() {
        usernameField.clear();
        passwordField.clear();
        roleCombo.setValue(null);
        selectedUser = null;
        usersTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}