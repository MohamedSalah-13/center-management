package com.codejava.center.controller;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.service.BackupService;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.util.FxAsync;
import com.codejava.commons.fx.dialog.AlertUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.time.LocalDate;

/**
 * كل إعدادات النظام في شاشة واحدة مقسّمة إلى تبويبات.
 *
 * <p>كانت الشاشة تعرض بيانات السنتر والنسخ الاحتياطي فقط، بينما
 * {@code ledgerStartDate} — وهو أخطر إعداد في النظام لأنه يعيد حساب أرصدة كل
 * الطلاب — لم تكن له واجهة إطلاقاً ولا يمكن تعديله إلا بـ SQL مباشر.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final BackupService backupService;
    private final NotificationService notificationService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${center.notifications.channel:whatsapp-link}")
    private String notificationChannel;

    @FXML private TextField centerNameField;
    @FXML private TextField centerPhoneField;
    @FXML private TextField logoPathField;
    @FXML private ImageView logoImageView;

    @FXML private TextField backupPathField;
    @FXML private CheckBox autoBackupCheckBox;

    @FXML private DatePicker ledgerStartDatePicker;

    @FXML private Label notificationChannelLabel;
    @FXML private Label databaseLabel;
    @FXML private Label dbUserLabel;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        showSystemInfo();
        loadSettings();
    }

    private void showSystemInfo() {
        notificationChannelLabel.setText(describeChannel());
        databaseLabel.setText(datasourceUrl);
        dbUserLabel.setText(datasourceUsername);
    }

    private String describeChannel() {
        String mode = notificationService.channelRequiresManualConfirmation()
                ? " — يفتح المحادثة ويضغط الموظف \"إرسال\""
                : " — إرسال مباشر";
        return notificationChannel + mode;
    }

    private void loadSettings() {
        FxAsync.supply(settingsService::getSettings, settings -> {
            centerNameField.setText(settings.getCenterName());
            centerPhoneField.setText(settings.getCenterPhone());
            backupPathField.setText(settings.getBackupPath());
            autoBackupCheckBox.setSelected(settings.isAutoBackupEnabled());
            ledgerStartDatePicker.setValue(settings.getLedgerStartDate());

            if (settings.getLogoPath() != null && !settings.getLogoPath().isBlank()) {
                logoPathField.setText(settings.getLogoPath());
                loadLogoImage(settings.getLogoPath());
            }
            statusLabel.setText("");
        }, error -> AlertUtils.showError("خطأ", "تعذر تحميل الإعدادات: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handleReload(ActionEvent event) {
        loadSettings();
    }

    @FXML
    public void handleBrowseLogo(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر شعار السنتر");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ملفات الصور", "*.png", "*.jpg", "*.jpeg"));

        File selectedFile = fileChooser.showOpenDialog(windowOf(event));
        if (selectedFile != null) {
            logoPathField.setText(selectedFile.getAbsolutePath());
            loadLogoImage(selectedFile.getAbsolutePath());
        }
    }

    private void loadLogoImage(String path) {
        File file = new File(path);
        if (!file.exists()) {
            // المسار محفوظ لكن الملف نُقل أو حُذف: المطبوعات ستخرج بلا شعار بصمت
            logoImageView.setImage(null);
            statusLabel.setText("تنبيه: ملف الشعار غير موجود في مساره المحفوظ.");
            return;
        }

        try {
            logoImageView.setImage(new Image(file.toURI().toString()));
        } catch (Exception e) {
            logoImageView.setImage(null);
            statusLabel.setText("تعذر عرض الشعار: " + e.getMessage());
        }
    }

    @FXML
    public void handleBrowseBackupPath(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("اختر مجلد حفظ النسخ الاحتياطية");

        File selectedDirectory = directoryChooser.showDialog(windowOf(event));
        if (selectedDirectory != null) {
            backupPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void handleClearLedgerDate(ActionEvent event) {
        ledgerStartDatePicker.setValue(null);
        statusLabel.setText("سيُحتسب كل الحركات بعد الحفظ.");
    }

    @FXML
    public void handleSaveSettings(ActionEvent event) {
        LocalDate ledgerStart = ledgerStartDatePicker.getValue();

        // تغيير تاريخ بداية الدفتر يعيد حساب أرصدة كل الطلاب وقد يمنع دخولهم فوراً
        if (ledgerStart != null && !AlertUtils.showConfirm("تأكيد",
                "تاريخ بداية الدفتر: " + ledgerStart + "\n\n"
                        + "ستُستبعد كل الحركات الأقدم من هذا التاريخ من حساب أرصدة الطلاب، "
                        + "وقد يُمنع من أصبح رصيده غير كافٍ من الدخول عبر البوابة.\n\nهل تريد المتابعة؟")) {
            return;
        }

        CenterSettings settings = CenterSettings.builder()
                .centerName(trimmed(centerNameField))
                .centerPhone(trimmed(centerPhoneField))
                .logoPath(trimmed(logoPathField))
                .backupPath(trimmed(backupPathField))
                .autoBackupEnabled(autoBackupCheckBox.isSelected())
                .ledgerStartDate(ledgerStart)
                .build();

        FxAsync.supply(() -> settingsService.save(settings), saved -> {
            statusLabel.setText("تم الحفظ.");
            AlertUtils.showSuccess("نجاح",
                    "تم حفظ الإعدادات.\nاسم السنتر وشعاره سيظهران في المطبوعات الجديدة.");
        }, error -> AlertUtils.showError("خطأ", "فشل حفظ الإعدادات: " + FxAsync.messageOf(error)));
    }

    private String trimmed(TextField field) {
        String text = field.getText();
        return text == null ? null : text.trim();
    }

    @FXML
    public void handleManualBackup(ActionEvent event) {
        String backupPath = backupPathField.getText();
        if (backupPath == null || backupPath.isBlank()) {
            AlertUtils.showWarning("تنبيه", "يرجى اختيار مجلد الحفظ أولاً.");
            return;
        }

        statusLabel.setText("جارٍ أخذ النسخة الاحتياطية...");
        FxAsync.supply(() -> backupService.executeBackup(backupPath), success -> {
            statusLabel.setText("");
            if (success) {
                AlertUtils.showSuccess("نجاح", "تم أخذ النسخة الاحتياطية في:\n" + backupPath);
            } else {
                AlertUtils.showError("خطأ", "فشلت عملية النسخ الاحتياطي.\n"
                        + "تأكد من وجود mysqldump ضمن مسار النظام ومن صلاحية الكتابة في المجلد.");
            }
        }, error -> {
            statusLabel.setText("");
            AlertUtils.showError("خطأ", FxAsync.messageOf(error));
        });
    }

    @FXML
    public void handleRestoreBackup(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر ملف النسخة الاحتياطية");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ملفات SQL", "*.sql"));

        File selectedFile = fileChooser.showOpenDialog(windowOf(event));
        if (selectedFile == null) {
            return;
        }

        // عملية مدمّرة لا رجعة فيها: التأكيد يذكر اسم الملف صراحةً
        if (!AlertUtils.showConfirm("تأكيد الاستعادة",
                "سيتم حذف كل البيانات الحالية واستبدالها بمحتوى:\n" + selectedFile.getName()
                        + "\n\nلا يمكن التراجع عن هذه العملية. هل أنت متأكد؟")) {
            return;
        }

        statusLabel.setText("جارٍ الاستعادة...");
        FxAsync.supply(() -> backupService.restoreBackup(selectedFile.getAbsolutePath()), success -> {
            statusLabel.setText("");
            if (success) {
                AlertUtils.showSuccess("نجاح",
                        "تمت الاستعادة.\nأعد تشغيل البرنامج حتى تُحمَّل البيانات الجديدة بالكامل.");
            } else {
                AlertUtils.showError("خطأ",
                        "فشلت الاستعادة. تأكد من صحة الملف ومن أن البرنامج مغلق على الأجهزة الأخرى.");
            }
        }, error -> {
            statusLabel.setText("");
            AlertUtils.showError("خطأ", FxAsync.messageOf(error));
        });
    }

    private Window windowOf(ActionEvent event) {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
