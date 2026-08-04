package com.codejava.center.controller;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.service.BackupService;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.DocumentKind;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.LanguageSelector;
import com.codejava.center.util.PrintPreferences;
import com.codejava.center.util.Printing;
import com.codejava.center.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.Printer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * كل إعدادات النظام في شاشة واحدة مقسّمة إلى تبويبات.
 *
 * <p>كانت الشاشة تعرض بيانات السنتر والنسخ الاحتياطي فقط، بينما
 * {@code ledgerStartDate} — وهو أخطر إعداد في النظام لأنه يعيد حساب أرصدة كل
 * الطلاب — لم تكن له واجهة إطلاقاً ولا يمكن تعديله إلا بـ SQL مباشر.</p>
 *
 * <p>تبويب اللغة هنا للاكتمال فقط: الاختيار متاح أيضاً من القائمة الجانبية ومن
 * شاشة الدخول، لأن هذه الشاشة مقصورة على المدير بينما اللغة تفضيل عرض لكل مشغّل.</p>
 *
 * <p>تبويبا اللغة والطباعة يُحفظان في تفضيلات الجهاز لحظة الاختيار، وبقية التبويبات
 * تُحفظ في قاعدة البيانات بزر "حفظ الإعدادات". التقسيم مقصود: الأولان يخصّان الجهاز
 * (لغة المشغّل، الطابعة الموصولة) والباقي يخصّ السنتر كله.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final BackupService backupService;
    private final NotificationService notificationService;
    private final ViewLoader viewLoader;

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

    @FXML private ComboBox<Locale> languageCombo;

    @FXML private ComboBox<PrintPreferences.PrintMode> printModeCombo;

    @FXML private ComboBox<String> reportPrinterCombo;
    @FXML private ComboBox<String> reportPaperCombo;
    @FXML private Label reportTargetLabel;
    @FXML private Label reportWarningLabel;

    @FXML private ComboBox<String> receiptPrinterCombo;
    @FXML private ComboBox<String> receiptPaperCombo;
    @FXML private Label receiptTargetLabel;
    @FXML private Label receiptWarningLabel;

    @FXML private TextField backupPathField;
    @FXML private CheckBox autoBackupCheckBox;

    @FXML private DatePicker ledgerStartDatePicker;

    @FXML private Label notificationChannelLabel;
    @FXML private Label databaseLabel;
    @FXML private Label dbUserLabel;
    @FXML private Label statusLabel;

    // ترويسة القائمة الجانبية تُبنى مرة عند فتح لوحة القيادة، فلا ترى اسماً أو شعاراً
    // حُفظ بعدها. نحتفظ بالقيمتين المحمَّلتين لنعرف هل تستدعي عملية الحفظ إعادة بنائها.
    private String loadedCenterName;
    private String loadedLogoPath;

    /** يمنع إعادة تعبئة قائمة الطابعات من أن تُحفظ كاختيار من المستخدم */
    private boolean reloadingPrinters;

    @FXML
    public void initialize() {
        // تبديل اللغة يعيد بناء لوحة القيادة، وهذه الشاشة تُعرض داخلها فتُغلق معها.
        // التعديلات غير المحفوظة في الحقول تضيع، ولهذا لا يُبدَّل قبل الحفظ عادةً.
        LanguageSelector.configure(languageCombo, this::reloadDashboard);

        configurePrinting();
        showSystemInfo();
        loadSettings();
    }

    /**
     * تبويب الطباعة.
     *
     * <p>لا يمرّ بزر "حفظ الإعدادات" لأنه لا يُحفظ في قاعدة البيانات أصلاً: الطابعة
     * مُثبَّتة على الجهاز لا على السنتر، فالاختيار يُحفظ لحظة تغييره في تفضيلات الجهاز
     * تماماً كاللغة. راجع {@link PrintPreferences}.</p>
     */
    private void configurePrinting() {
        printModeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(PrintPreferences.PrintMode mode) {
                return mode == null ? "" : mode.getDisplayName();
            }

            @Override
            public PrintPreferences.PrintMode fromString(String string) {
                return null;
            }
        });
        printModeCombo.getItems().setAll(PrintPreferences.PrintMode.values());
        printModeCombo.setValue(PrintPreferences.mode());
        printModeCombo.valueProperty().addListener((observable, oldMode, newMode) -> {
            if (newMode != null) {
                PrintPreferences.setMode(newMode);
                statusLabel.setText(I18n.get("settings.printSaved"));
            }
        });

        configureKind(DocumentKind.REPORT, reportPrinterCombo, reportPaperCombo,
                reportTargetLabel, reportWarningLabel);
        configureKind(DocumentKind.RECEIPT, receiptPrinterCombo, receiptPaperCombo,
                receiptTargetLabel, receiptWarningLabel);
    }

    /**
     * يربط قائمتَي الطابعة والورق لنوع مستند واحد.
     *
     * <p>النوعان يتصرفان تصرفاً واحداً - نفس المحوّلات ونفس الحارس ونفس ترتيب "غيّر الطابعة
     * فتتغيّر معها مقاسات الورق" - فكتابتهما مرتين كان يعني موضعين لنسيان الحارس.</p>
     */
    private void configureKind(DocumentKind kind, ComboBox<String> printerCombo,
                               ComboBox<String> paperCombo, Label targetLabel, Label warningLabel) {
        printerCombo.setConverter(nameConverter("settings.printerSystemDefault"));
        paperCombo.setConverter(nameConverter("settings.paperPrinterDefault"));

        loadKind(kind, printerCombo, paperCombo, targetLabel, warningLabel);

        printerCombo.valueProperty().addListener((observable, oldName, newName) -> {
            // إعادة تعبئة القائمة تُفرغ الاختيار ثم تعيده، فتُطلق الحدث مرتين: مرة بـ null
            // تمحو الاختيار المحفوظ. الحارس يقصر الحفظ على اختيار المستخدم وحده.
            if (reloadingPrinters) {
                return;
            }
            PrintPreferences.setPrinterName(kind, newName);
            // مقاسات الورق تخصّ الطابعة، فتغييرها يُبطل القائمة المعروضة
            loadKind(kind, printerCombo, paperCombo, targetLabel, warningLabel);
            statusLabel.setText(I18n.get("settings.printSaved"));
        });

        paperCombo.valueProperty().addListener((observable, oldPaper, newPaper) -> {
            if (reloadingPrinters) {
                return;
            }
            PrintPreferences.setPaperName(kind, newPaper);
            targetLabel.setText(Printing.describeTarget(kind));
            statusLabel.setText(I18n.get("settings.printSaved"));
        });
    }

    /** محوّل عرض يُظهر {@code null} باسم الخيار الافتراضي بدل أن يتركه فراغاً */
    private StringConverter<String> nameConverter(String defaultKey) {
        return new StringConverter<>() {
            @Override
            public String toString(String name) {
                return name == null ? I18n.get(defaultKey) : name;
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        };
    }

    /**
     * تعبئة الطابعات ومقاسات الورق لنوع واحد.
     *
     * <p>المقاسات تأتي من الطابعة المختارة نفسها، فتظهر مقاسات الرول الحقيقية للطابعة
     * الحرارية و A4/A5 لطابعة المكتب. الطابعة أو المقاس المحفوظ قد يكون قد اختفى، فيبقى
     * مختاراً في القائمة بينما تُنبّه الشاشة إلى غيابه بدل أن تُسقطه بصمت.</p>
     */
    private void loadKind(DocumentKind kind, ComboBox<String> printerCombo,
                          ComboBox<String> paperCombo, Label targetLabel, Label warningLabel) {
        String savedPrinter = PrintPreferences.printerName(kind);
        boolean printerMissing = PrintPreferences.savedPrinterIsMissing(kind);

        List<String> printerNames = new ArrayList<>();
        printerNames.add(null); // خيار "الطابعة الافتراضية للنظام"
        Printer.getAllPrinters().forEach(printer -> printerNames.add(printer.getName()));
        if (printerMissing) {
            printerNames.add(savedPrinter);
        }

        Printer resolved = PrintPreferences.resolvePrinter(kind);
        String savedPaper = PrintPreferences.paperName(kind);
        boolean paperMissing = PrintPreferences.savedPaperIsMissing(kind, resolved);

        List<String> paperNames = new ArrayList<>();
        paperNames.add(null); // خيار "مقاس الطابعة الافتراضي"
        PrintPreferences.supportedPapers(resolved).forEach(paper -> paperNames.add(paper.getName()));
        if (paperMissing) {
            paperNames.add(savedPaper);
        }

        reloadingPrinters = true;
        try {
            printerCombo.getItems().setAll(printerNames);
            printerCombo.setValue(savedPrinter);
            paperCombo.getItems().setAll(paperNames);
            paperCombo.setValue(savedPaper);
        } finally {
            reloadingPrinters = false;
        }

        targetLabel.setText(Printing.describeTarget(kind));

        // التنبيه له مكانه في التبويب نفسه لا في شريط الحالة المشترك: شريط الحالة يُمسح
        // عند اكتمال تحميل بقية الإعدادات، فكان التنبيه يظهر ثم يختفي وحده
        String warning = printerMissing ? I18n.format("settings.printerMissing", savedPrinter)
                : paperMissing ? I18n.format("settings.paperMissing", savedPaper)
                : "";
        warningLabel.setText(warning);
        warningLabel.setVisible(!warning.isEmpty());
        warningLabel.setManaged(!warning.isEmpty());
    }

    @FXML
    public void handleRefreshPrinters(ActionEvent event) {
        loadKind(DocumentKind.REPORT, reportPrinterCombo, reportPaperCombo,
                reportTargetLabel, reportWarningLabel);
        loadKind(DocumentKind.RECEIPT, receiptPrinterCombo, receiptPaperCombo,
                receiptTargetLabel, receiptWarningLabel);
    }

    @FXML
    public void handlePrintReportTestPage(ActionEvent event) {
        printTestPage(DocumentKind.REPORT, event);
    }

    @FXML
    public void handlePrintReceiptTestPage(ActionEvent event) {
        printTestPage(DocumentKind.RECEIPT, event);
    }

    private void printTestPage(DocumentKind kind, ActionEvent event) {
        try {
            // الطباعة تبقى على خيط الواجهة: شرط PrinterJob في JavaFX
            Printing.printTestPage(kind, windowOf(event));
        } catch (Exception e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
        }
    }

    private void showSystemInfo() {
        notificationChannelLabel.setText(describeChannel());
        databaseLabel.setText(datasourceUrl);
        dbUserLabel.setText(datasourceUsername);
    }

    private String describeChannel() {
        String mode = I18n.get(notificationService.channelRequiresManualConfirmation()
                ? "settings.channelManual" : "settings.channelAutomatic");
        return I18n.format("settings.channel", notificationChannel, mode);
    }

    private void reloadDashboard() {
        try {
            viewLoader.showDashboard((Stage) languageCombo.getScene().getWindow());
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(FxAsync.messageOf(e));
        }
    }

    private void loadSettings() {
        FxAsync.supply(settingsService::getSettings, settings -> {
            loadedCenterName = settings.getCenterName();
            loadedLogoPath = settings.getLogoPath();

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
        }, error -> Dialogs.error(I18n.format("settings.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleReload(ActionEvent event) {
        loadSettings();
    }

    @FXML
    public void handleBrowseLogo(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("settings.chooseLogo"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18n.get("settings.imageFiles"), "*.png", "*.jpg", "*.jpeg"));

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
            statusLabel.setText(I18n.get("settings.logoMissing"));
            return;
        }

        try {
            logoImageView.setImage(new Image(file.toURI().toString()));
        } catch (Exception e) {
            logoImageView.setImage(null);
            statusLabel.setText(I18n.format("settings.logoDisplayFailed", e.getMessage()));
        }
    }

    @FXML
    public void handleBrowseBackupPath(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(I18n.get("settings.chooseBackupDir"));

        File selectedDirectory = directoryChooser.showDialog(windowOf(event));
        if (selectedDirectory != null) {
            backupPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void handleClearLedgerDate(ActionEvent event) {
        ledgerStartDatePicker.setValue(null);
        statusLabel.setText(I18n.get("settings.ledgerCleared"));
    }

    @FXML
    public void handleSaveSettings(ActionEvent event) {
        LocalDate ledgerStart = ledgerStartDatePicker.getValue();

        // تغيير تاريخ بداية الدفتر يعيد حساب أرصدة كل الطلاب وقد يمنع دخولهم فوراً
        if (ledgerStart != null && !Dialogs.confirm(I18n.get("common.confirm"),
                I18n.format("settings.ledgerConfirm", ledgerStart))) {
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

        boolean brandingChanged = !java.util.Objects.equals(loadedCenterName, settings.getCenterName())
                || !java.util.Objects.equals(loadedLogoPath, settings.getLogoPath());

        FxAsync.supply(() -> settingsService.save(settings), saved -> {
            statusLabel.setText(I18n.get("settings.saved"));
            Dialogs.success(I18n.get("settings.savedDetail"));

            // إعادة بناء اللوحة تعيدنا إلى الرئيسية، فلا تُنفَّذ إلا حين تغيّرت الترويسة فعلاً
            if (brandingChanged) {
                reloadDashboard();
            }
        }, error -> Dialogs.error(I18n.format("settings.saveFailed", FxAsync.messageOf(error))));
    }

    private String trimmed(TextField field) {
        String text = field.getText();
        return text == null ? null : text.trim();
    }

    @FXML
    public void handleManualBackup(ActionEvent event) {
        String backupPath = backupPathField.getText();
        if (backupPath == null || backupPath.isBlank()) {
            Dialogs.warning(I18n.get("settings.selectBackupDirFirst"));
            return;
        }

        statusLabel.setText(I18n.get("settings.backupRunning"));
        FxAsync.supply(() -> backupService.executeBackup(backupPath), success -> {
            statusLabel.setText("");
            if (success) {
                Dialogs.success(I18n.format("settings.backupDone", backupPath));
            } else {
                Dialogs.error(I18n.get("settings.backupFailed"));
            }
        }, error -> {
            statusLabel.setText("");
            Dialogs.error(FxAsync.messageOf(error));
        });
    }

    @FXML
    public void handleRestoreBackup(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("settings.chooseBackupFile"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18n.get("settings.sqlFiles"), "*.sql"));

        File selectedFile = fileChooser.showOpenDialog(windowOf(event));
        if (selectedFile == null) {
            return;
        }

        // عملية مدمّرة لا رجعة فيها: التأكيد يذكر اسم الملف صراحةً
        if (!Dialogs.confirm(I18n.get("settings.restoreConfirmTitle"),
                I18n.format("settings.restoreConfirm", selectedFile.getName()))) {
            return;
        }

        statusLabel.setText(I18n.get("settings.restoreRunning"));
        FxAsync.supply(() -> backupService.restoreBackup(selectedFile.getAbsolutePath()), success -> {
            statusLabel.setText("");
            if (success) {
                Dialogs.success(I18n.get("settings.restoreDone"));
            } else {
                Dialogs.error(I18n.get("settings.restoreFailed"));
            }
        }, error -> {
            statusLabel.setText("");
            Dialogs.error(FxAsync.messageOf(error));
        });
    }

    private Window windowOf(ActionEvent event) {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
