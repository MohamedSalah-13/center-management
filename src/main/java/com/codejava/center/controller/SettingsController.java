package com.codejava.center.controller;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.service.BackupService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final CenterSettingsRepository settingsRepository;
    private final BackupService backupService;

    @FXML
    private TextField centerNameField;
    @FXML
    private TextField centerPhoneField;
    @FXML
    private TextField logoPathField;
    @FXML
    private ImageView logoImageView;

    @FXML
    private TextField backupPathField;
    @FXML
    private CheckBox autoBackupCheckBox;

    @FXML
    public void initialize() {
        // تحميل الإعدادات عند فتح الشاشة
        CompletableFuture.supplyAsync(() -> settingsRepository.findById(1L).orElse(new CenterSettings()))
                .thenAccept(settings -> Platform.runLater(() -> {
                    centerNameField.setText(settings.getCenterName());
                    centerPhoneField.setText(settings.getCenterPhone());
                    backupPathField.setText(settings.getBackupPath());
                    autoBackupCheckBox.setSelected(settings.isAutoBackupEnabled());

                    if (settings.getLogoPath() != null && !settings.getLogoPath().isEmpty()) {
                        logoPathField.setText(settings.getLogoPath());
                        loadLogoImage(settings.getLogoPath());
                    }
                }));
    }

    @FXML
    public void handleBrowseLogo(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر شعار السنتر");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        Window window = ((Node) event.getSource()).getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            String path = selectedFile.getAbsolutePath();
            logoPathField.setText(path);
            loadLogoImage(path);
        }
    }

    private void loadLogoImage(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                Image image = new Image(file.toURI().toString());
                logoImageView.setImage(image);
            }
        } catch (Exception e) {
            System.err.println("فشل تحميل الصورة: " + e.getMessage());
        }
    }

    @FXML
    public void handleBrowseBackupPath(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("اختر مجلد حفظ النسخ الاحتياطية");

        Window window = ((Node) event.getSource()).getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(window);

        if (selectedDirectory != null) {
            backupPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void handleSaveSettings(ActionEvent event) {
        CenterSettings settings = CenterSettings.builder()
                .id(1L) // ثابت لتحديث نفس الصف
                .centerName(centerNameField.getText().trim())
                .centerPhone(centerPhoneField.getText().trim())
                .logoPath(logoPathField.getText().trim())
                .backupPath(backupPathField.getText().trim())
                .autoBackupEnabled(autoBackupCheckBox.isSelected())
                .build();

        CompletableFuture.runAsync(() -> settingsRepository.save(settings))
                .thenRun(() -> Platform.runLater(() ->
                        showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ الإعدادات بنجاح.")
                ))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "خطأ", "فشل حفظ الإعدادات: " + ex.getMessage()));
                    return null;
                });
    }

    @FXML
    public void handleManualBackup(ActionEvent event) {
        String backupPath = backupPathField.getText();
        if (backupPath == null || backupPath.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار مسار الحفظ أولاً.");
            return;
        }

        CompletableFuture.supplyAsync(() -> backupService.executeBackup(backupPath))
                .thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم أخذ النسخة الاحتياطية بنجاح.");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "خطأ", "فشلت عملية النسخ الاحتياطي. يرجى التأكد من مسار MySQL (mysqldump) في النظام.");
                    }
                }));
    }

    @FXML
    public void handleRestoreBackup(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر ملف النسخة الاحتياطية (.sql)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Files", "*.sql"));

        Window window = ((Node) event.getSource()).getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            // رسالة تحذيرية قبل الاستعادة لأنها ستمسح البيانات الحالية
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "تحذير هام: استعادة النسخة الاحتياطية ستؤدي إلى حذف البيانات الحالية واستبدالها ببيانات النسخة المحددة. هل أنت متأكد من المتابعة؟",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("تأكيد الاستعادة");
            confirm.setHeaderText("تأكيد الكتابة فوق البيانات الحالية");

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    // تنفيذ الاستعادة في خلفية النظام
                    CompletableFuture.supplyAsync(() -> backupService.restoreBackup(selectedFile.getAbsolutePath()))
                            .thenAccept(success -> Platform.runLater(() -> {
                                if (success) {
                                    showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم استعادة النسخة الاحتياطية بنجاح! يرجى إعادة تشغيل البرنامج لضمان تحديث البيانات.");
                                } else {
                                    showAlert(Alert.AlertType.ERROR, "خطأ", "فشلت عملية الاستعادة. يرجى التأكد من صحة الملف.");
                                }
                            }));
                }
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}