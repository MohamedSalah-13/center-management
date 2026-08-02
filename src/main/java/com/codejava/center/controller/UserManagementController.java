package com.codejava.center.controller;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.UserService;
import com.codejava.center.util.FxAsync;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class UserManagementController {

    private final UserService userService;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<Role> roleCombo;

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colId, colUsername, colRole;
    @FXML private Button updateButton, deleteButton;

    private User selectedUser = null;
    private final ObservableList<User> usersList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // تحديد الصلاحيات المتاحة في النظام
        roleCombo.getItems().addAll(Role.values());
        roleCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Role role) {
                return role == null ? "" : role.getArabicName();
            }

            @Override
            public Role fromString(String string) {
                return null;
            }
        });

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));

        // ترجمة الصلاحيات لتعرض بالعربية في الجدول
        colRole.setCellValueFactory(data -> {
            Role role = data.getValue().getRole();
            return new SimpleStringProperty(role == null ? "" : role.getArabicName());
        });

        usersTable.setItems(usersList);
        setupTableSelectionListener();
        loadData();
    }

    private void loadData() {
        FxAsync.supply(userService::getAllUsers,
                users -> usersList.setAll(users),
                error -> showAlert(Alert.AlertType.ERROR, "خطأ", "تعذر تحميل المستخدمين: " + FxAsync.messageOf(error)));
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
        Role role = roleCombo.getValue();

        if (username.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إدخال اسم المستخدم واختيار الصلاحية.");
            return;
        }

        user.setUsername(username);
        user.setRole(role);

        boolean isNew = user.getId() == null;
        FxAsync.supply(() -> userService.saveUser(user, password), saved -> {
            if (isNew) {
                usersList.add(saved);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تمت إضافة المستخدم بنجاح.");
            } else {
                int idx = usersList.indexOf(user);
                if (idx >= 0) {
                    usersList.set(idx, saved);
                }
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم تحديث بيانات المستخدم.");
            }
            clearForm();
        }, error -> showAlert(Alert.AlertType.ERROR, "خطأ", FxAsync.messageOf(error)));
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
                User target = selectedUser;
                FxAsync.run(() -> userService.deleteUser(target.getId()), () -> {
                    usersList.remove(target);
                    clearForm();
                }, error -> showAlert(Alert.AlertType.ERROR, "خطأ", FxAsync.messageOf(error)));
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