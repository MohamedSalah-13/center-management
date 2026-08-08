package com.codejava.center.controller;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.BackupFrequency;
import com.codejava.center.domain.enums.Currency;
import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.service.BackupRetention;
import com.codejava.center.service.BackupSchedule;
import com.codejava.center.service.BackupService;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.notification.HttpGatewaySender;
import com.codejava.center.service.notification.NotificationConfig;
import com.codejava.center.service.notification.WhatsAppCloudApiSender;
import com.codejava.center.service.notification.WhatsAppLink;
import com.codejava.center.service.notification.WhatsAppLinkStyle;
import com.codejava.center.util.BackupCrypto;
import com.codejava.center.util.BackupPreferences;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.DocumentKind;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.LanguageSelector;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.NotificationPreferences;
import com.codejava.center.util.PrintPreferences;
import com.codejava.center.util.Printing;
import com.codejava.center.util.UiScaleSelector;
import com.codejava.center.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.Printer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
 *
 * <p>تبويب النسخ الاحتياطي وحده يجمع الاثنين: الموعد وعدد النسخ في قاعدة البيانات،
 * وكلمة مرور التشفير في تفضيلات الجهاز - لأنها لا يصح أن تُحفظ داخل القاعدة التي
 * تحميها. كلاهما يُحفظ بزر "حفظ الإعدادات": حقل كلمة مرور لا يُحفظ عند كل حرف.</p>
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

    @FXML private TextField centerNameField;
    @FXML private TextField centerPhoneField;
    @FXML private TextField logoPathField;
    @FXML private ImageView logoImageView;
    @FXML private ComboBox<Currency> currencyCombo;

    @FXML private ComboBox<Locale> languageCombo;
    @FXML private ComboBox<Double> fontScaleCombo;

    @FXML private ComboBox<PrintPreferences.PrintMode> printModeCombo;
    @FXML private CheckBox directSheetPrintCheck;
    @FXML private CheckBox centerHeaderCheck;

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

    @FXML private GridPane scheduleGrid;
    @FXML private ComboBox<BackupFrequency> backupFrequencyCombo;
    @FXML private Spinner<Integer> backupHourSpinner;
    @FXML private Spinner<Integer> backupMinuteSpinner;
    @FXML private Label backupDayOfWeekLabel;
    @FXML private ComboBox<Integer> backupDayOfWeekCombo;
    @FXML private Label backupDayOfMonthLabel;
    @FXML private Spinner<Integer> backupDayOfMonthSpinner;
    @FXML private Spinner<Integer> backupRetentionSpinner;
    @FXML private Label backupNextRunLabel;
    @FXML private Label backupLastRunLabel;

    @FXML private CheckBox backupEncryptCheckBox;
    @FXML private PasswordField backupPassphraseField;
    @FXML private PasswordField backupPassphraseConfirmField;

    @FXML private ComboBox<NotificationChannel> notifChannelCombo;
    @FXML private Label notifStateLabel;
    @FXML private Label notifChannelNoteLabel;
    @FXML private Label notifProviderNoteLabel;

    @FXML private VBox notifLinkBox;
    @FXML private ComboBox<WhatsAppLinkStyle> notifLinkStyleCombo;
    @FXML private Label notifLinkTemplateLabel;
    @FXML private TextField notifLinkTemplateField;
    @FXML private Label notifLinkPreviewLabel;

    @FXML private VBox notifProviderBox;
    @FXML private TextField notifApiUrlField;
    @FXML private TextField notifSenderIdField;
    @FXML private PasswordField notifTokenField;
    @FXML private Label notifTemplateNameLabel;
    @FXML private TextField notifTemplateNameField;
    @FXML private Label notifTemplateLanguageLabel;
    @FXML private TextField notifTemplateLanguageField;
    @FXML private Label notifBodyTemplateLabel;
    @FXML private TextField notifBodyTemplateField;

    @FXML private TextField notifTestPhoneField;

    @FXML private DatePicker ledgerStartDatePicker;

    @FXML private Label notificationChannelLabel;
    @FXML private Label databaseLabel;
    @FXML private Label dbUserLabel;
    @FXML private Label statusLabel;

    // ترويسة القائمة الجانبية تُبنى مرة عند فتح لوحة القيادة، فلا ترى اسماً أو شعاراً
    // حُفظ بعدها. نحتفظ بالقيمتين المحمَّلتين لنعرف هل تستدعي عملية الحفظ إعادة بنائها.
    private String loadedCenterName;
    private String loadedLogoPath;

    /**
     * العملة كما جاءت من القاعدة، للمقارنة عند الحفظ.
     * تبديلها يغيّر ما تعنيه كل المبالغ المحفوظة بلا أن يمسّ رقماً واحداً منها، فلا يمرّ
     * بلا تأكيد صريح - نفس معاملة {@code ledgerStartDate}.
     */
    private Currency loadedCurrency;

    /**
     * تُحفظ كما جاءت: الشاشة تبني كائن إعدادات جديداً بالكامل عند الحفظ، فأي حقل لا تعرضه
     * يُكتب فارغاً فوق القيمة الموجودة. لحظة آخر نسخة تلقائية يكتبها المجدوِل لا المستخدم.
     */
    private LocalDateTime loadedLastAutoBackupAt;

    /** يمنع إعادة تعبئة قائمة الطابعات من أن تُحفظ كاختيار من المستخدم */
    private boolean reloadingPrinters;

    /** يمنع تعبئة حقول الجدولة من المحفوظ من أن تظهر كأنها تعديل لم يُحفظ بعد */
    private boolean loadingSchedule;

    /** حالة التشفير كما قُرئت من الجهاز، لنعرف هل غيّرها المستخدم فعلاً */
    private boolean loadedEncryptionEnabled;
    private String loadedPassphrase = "";

    /** يمنع تعبئة حقول الإشعارات من المحفوظ من أن تظهر كأنها تعديل لم يُحفظ بعد */
    private boolean loadingNotifications;

    /**
     * هل عُدّلت حقول الإشعارات بعد آخر حفظ؟
     * زر "إرسال تجريبي" يمرّ بالخدمة فيقرأ المحفوظ لا ما في الحقول، واختبارٌ يقيس ضبطاً
     * غير الذي يراه المستخدم أمامه أسوأ من ألا يكون هناك اختبار.
     */
    private boolean notificationFieldsChanged;

    /** القناة المحفوظة، لنعرف هل حوّل المستخدم الإرسال إلى قناة تلقائية في هذه الجلسة */
    private NotificationChannel loadedNotificationChannel;

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** أدنى طول مقبول لكلمة مرور النسخة؛ ما دونه لا يصمد أمام تخمين آلي */
    private static final int MIN_PASSPHRASE_LENGTH = 8;

    /**
     * عدد النسخ المقترح للاحتفاظ بها: شهر من النسخ اليومية يكفي لاكتشاف خطأ والرجوع قبله،
     * ولا يملأ قرصاً صغيراً.
     *
     * <p>يُقرأ من {@link BackupRetention} لا يُكتب هنا: كان رقماً مكرَّراً في الشاشة وحدها
     * بينما تقرأ الخدمة القيمة الغائبة على أنها "احتفظ بالكل"، فكانت الشاشة تعرض ثلاثين
     * ولا تُحذف نسخة واحدة أبداً. نفس قاعدة {@link #showBackupSchedule} في المواعيد.</p>
     */
    private static final int DEFAULT_RETENTION = BackupRetention.DEFAULT_COUNT;

    @FXML
    public void initialize() {
        // تبديل اللغة يعيد بناء لوحة القيادة، وهذه الشاشة تُعرض داخلها فتُغلق معها.
        // التعديلات غير المحفوظة في الحقول تضيع، ولهذا لا يُبدَّل قبل الحفظ عادةً.
        LanguageSelector.configure(languageCombo, this::reloadDashboard);

        // حجم الخط لا يعيد البناء ولا يمرّ بزرّ الحفظ: تفضيل جهاز يسري لحظة اختياره،
        // ولا يصحّ أن يُلزم من يريد قراءة الشاشة بحفظ إعدادات السنتر كلها معه
        UiScaleSelector.configure(fontScaleCombo);

        configureCurrency();
        configurePrinting();
        configureBackup();
        configureNotifications();
        showSystemInfo();
        loadSettings();
    }

    /**
     * قائمة العملات في تبويب بيانات السنتر.
     *
     * <p>تُبنى من {@code Currency.values()} لا من قائمة مكتوبة في FXML: عملة تُضاف مستقبلاً
     * تظهر هنا بلا لمس الشاشة - نفس ما يفعله سجل قواعد التنبيهات مع أنواعها.</p>
     *
     * <p>الاسم والرمز معاً في السطر لأن الرمز وحده لا يكفي ("$" لأكثر من عشرين دولة)
     * والاسم وحده لا يُري المستخدم ما سيُطبع فعلاً على الإيصال.</p>
     *
     * <p>العملة إعداد سنتر يُحفظ بزر "حفظ الإعدادات" لا لحظة الاختيار كالطابعة واللغة:
     * هي رقم يقرؤه كل من في السنتر، لا تفضيلَ عرض على هذا الجهاز.</p>
     */
    private void configureCurrency() {
        currencyCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Currency currency) {
                return currency == null ? ""
                        : currency.getDisplayName() + " (" + currency.getSymbol() + ")";
            }

            @Override
            public Currency fromString(String string) {
                return null;
            }
        });
        currencyCombo.getItems().setAll(Currency.values());

        // القيمة السارية فعلاً حتى تصل قراءة الإعدادات: الحفظ قبل اكتمالها لا يصحّ أن
        // يقارن بـ null فيبدو كأن المستخدم بدّل العملة وهو لم يلمس القائمة
        loadedCurrency = MoneyUtils.currency();
        currencyCombo.setValue(loadedCurrency);
    }

    /**
     * تبويب الإشعارات.
     *
     * <p>كل ما فيه يُحفظ بزر "حفظ الإعدادات" ولو كان نصفه يخصّ الجهاز ونصفه السنتر:
     * القناة والعناوين والقوالب في قاعدة البيانات، وشكل الرابط ومفتاح الدخول في تفضيلات
     * الجهاز. الحفظ لحظة التعديل - كما في تبويب الطباعة - لا يصلح هنا لأن الحقول متعلّقة
     * ببعضها: مفتاح بلا معرّف مرسل، أو قناة مزوّد قبل كتابة عنوانه، حالتان لا معنى لهما.</p>
     *
     * <p>وسبب انقسام التخزين في {@link NotificationPreferences}: المفتاح سرّ لا يُحفظ
     * داخل قاعدة تخرج نسخها على فلاشة، وشكل الرابط يتبع ما هو مثبَّت على هذا الجهاز.</p>
     */
    private void configureNotifications() {
        notifChannelCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(NotificationChannel channel) {
                return channel == null ? "" : channel.getDisplayName();
            }

            @Override
            public NotificationChannel fromString(String string) {
                return null;
            }
        });
        notifChannelCombo.getItems().setAll(NotificationChannel.values());
        notifChannelCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            showNotificationFieldsFor(newValue);
            notificationFieldChanged();
        });

        notifLinkStyleCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(WhatsAppLinkStyle style) {
                return style == null ? "" : style.getDisplayName();
            }

            @Override
            public WhatsAppLinkStyle fromString(String string) {
                return null;
            }
        });
        notifLinkStyleCombo.getItems().setAll(WhatsAppLinkStyle.values());
        notifLinkStyleCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            setShown(newValue == WhatsAppLinkStyle.CUSTOM, notifLinkTemplateLabel, notifLinkTemplateField);
            updateLinkPreview();
            notificationFieldChanged();
        });

        notifLinkTemplateField.textProperty().addListener((observable, oldText, newText) -> {
            updateLinkPreview();
            notificationFieldChanged();
        });

        for (TextField field : new TextField[]{notifApiUrlField, notifSenderIdField,
                notifTemplateNameField, notifTemplateLanguageField, notifBodyTemplateField}) {
            field.textProperty().addListener((observable, oldText, newText) -> notificationFieldChanged());
        }
        notifTokenField.textProperty().addListener((observable, oldText, newText) -> notificationFieldChanged());

        // تُملأ من المحفوظ حتى لا يعيد المستخدم كتابتها كلما حفظ إعداداً آخر - كما في
        // كلمة مرور النسخ الاحتياطية تماماً
        loadingNotifications = true;
        try {
            notifLinkStyleCombo.setValue(NotificationPreferences.linkStyle());
            notifLinkTemplateField.setText(NotificationPreferences.linkTemplate());
            notifTokenField.setText(storedApiToken());
        } finally {
            loadingNotifications = false;
        }

        // إعدادات السنتر تصل بعد لحظة (قراءة على خيط خلفي)، وحتى تصل تُعرض حقول القناة
        // الافتراضية بدل عرض كل الحقول معاً ثم اختفاء نصفها أمام المستخدم
        showNotificationFieldsFor(null);
    }

    private String storedApiToken() {
        char[] saved = NotificationPreferences.apiToken();
        if (saved == null) {
            return "";
        }
        String value = new String(saved);
        java.util.Arrays.fill(saved, '\0');
        return value;
    }

    private void notificationFieldChanged() {
        if (loadingNotifications) {
            return;
        }
        notificationFieldsChanged = true;
        statusLabel.setText(I18n.get("settings.notif.unsaved"));
    }

    /** كل قناة وحقولها: عرض حقل لا تقرأه القناة المختارة يوحي بأنه مؤثّر */
    private void showNotificationFieldsFor(NotificationChannel channel) {
        NotificationChannel selected = channel == null ? NotificationChannel.WHATSAPP_LINK : channel;

        setShown(selected == NotificationChannel.WHATSAPP_LINK, notifLinkBox);
        setShown(selected != NotificationChannel.WHATSAPP_LINK, notifProviderBox);
        setShown(selected == NotificationChannel.WHATSAPP_CLOUD_API,
                notifTemplateNameLabel, notifTemplateNameField,
                notifTemplateLanguageLabel, notifTemplateLanguageField);
        setShown(selected == NotificationChannel.HTTP_GATEWAY,
                notifBodyTemplateLabel, notifBodyTemplateField);

        notifChannelNoteLabel.setText(I18n.get("settings.notif.note." + selected.name()));
        notifProviderNoteLabel.setText(selected == NotificationChannel.WHATSAPP_CLOUD_API
                ? I18n.get("settings.notif.cloudNote") : I18n.get("settings.notif.gatewayNote"));

        // العنوان الافتراضي يختلف اختلافاً تاماً بين واجهة Meta ووسيط محلي، فالتلميح
        // في الحقل نفسه لا في ملاحظة أسفل الشاشة
        notifApiUrlField.setPromptText(selected == NotificationChannel.WHATSAPP_CLOUD_API
                ? WhatsAppCloudApiSender.DEFAULT_BASE_URL
                : I18n.get("settings.notif.apiUrlPrompt"));
        notifSenderIdField.setPromptText(I18n.get(selected == NotificationChannel.WHATSAPP_CLOUD_API
                ? "settings.notif.senderIdCloudPrompt" : "settings.notif.senderIdGatewayPrompt"));
        notifBodyTemplateField.setPromptText(HttpGatewaySender.DEFAULT_BODY_TEMPLATE);
    }

    /**
     * يعرض الرابط كما سيُفتح فعلاً.
     * "wa.me" وحدها لا تجيب سؤال من يكتب قالباً بيده: ما الذي سيفتحه البرنامج؟ والقالب
     * الناقص يظهر هنا رسالةَ خطأ بدل أن يظهر عند أول ولي أمر.
     */
    private void updateLinkPreview() {
        try {
            notifLinkPreviewLabel.setText(WhatsAppLink.build(
                    notifLinkStyleCombo.getValue() == null ? WhatsAppLinkStyle.WA_ME : notifLinkStyleCombo.getValue(),
                    notifLinkTemplateField.getText(),
                    "201000000000",
                    I18n.get("settings.notif.previewMessage")));
            notifLinkPreviewLabel.getStyleClass().remove("danger-text");
        } catch (IllegalArgumentException e) {
            notifLinkPreviewLabel.setText(e.getMessage());
            if (!notifLinkPreviewLabel.getStyleClass().contains("danger-text")) {
                notifLinkPreviewLabel.getStyleClass().add("danger-text");
            }
        }
    }

    /**
     * تبويب النسخ الاحتياطي.
     *
     * <p>الجدولة تُحفظ في قاعدة البيانات مع بقية إعدادات السنتر بزر "حفظ الإعدادات".
     * أما التشفير - رغم أنه يُحفظ لكل جهاز مثل الطابعة واللغة - فيُحفظ بالزر نفسه لا
     * لحظة التعديل: حقل كلمة مرور لا يمكن حفظه عند كل حرف يُكتب، ولا يصحّ حفظ كلمة
     * قبل مطابقتها بحقل التأكيد. راجع {@link BackupPreferences} لسبب كونه لكل جهاز.</p>
     */
    private void configureBackup() {
        backupFrequencyCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(BackupFrequency frequency) {
                return frequency == null ? "" : frequency.getDisplayName();
            }

            @Override
            public BackupFrequency fromString(String string) {
                return null;
            }
        });
        backupFrequencyCombo.getItems().setAll(BackupFrequency.values());
        backupFrequencyCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            showFieldsFor(newValue);
            scheduleFieldChanged();
        });

        backupDayOfWeekCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer day) {
                return day == null ? "" : BackupSchedule.dayOfWeekName(day);
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });
        backupDayOfWeekCombo.getItems().setAll(1, 2, 3, 4, 5, 6, 7);
        backupDayOfWeekCombo.valueProperty().addListener((observable, oldValue, newValue) -> scheduleFieldChanged());

        configureSpinner(backupHourSpinner, 0, 23, BackupSchedule.DEFAULT_TIME.getHour(), true);
        configureSpinner(backupMinuteSpinner, 0, 59, BackupSchedule.DEFAULT_TIME.getMinute(), true);
        configureSpinner(backupDayOfMonthSpinner, 1, 31, BackupSchedule.DEFAULT_DAY_OF_MONTH, false);
        configureSpinner(backupRetentionSpinner,
                BackupRetention.KEEP_ALL, BackupRetention.MAX_COUNT, DEFAULT_RETENTION, false);

        // بلا مجلد ولا تفعيل لا معنى لضبط موعد: الحقول تبقى ظاهرة لتُقرأ، لكنها معطَّلة
        scheduleGrid.disableProperty().bind(autoBackupCheckBox.selectedProperty().not());

        loadedEncryptionEnabled = BackupPreferences.encryptionEnabled();
        backupEncryptCheckBox.setSelected(loadedEncryptionEnabled);

        char[] saved = BackupPreferences.passphrase();
        if (saved != null) {
            // تُملأ الحقول بالمحفوظ حتى لا يضطر المستخدم لإعادة كتابتها كلما حفظ إعداداً آخر
            loadedPassphrase = new String(saved);
            backupPassphraseField.setText(loadedPassphrase);
            backupPassphraseConfirmField.setText(loadedPassphrase);
            java.util.Arrays.fill(saved, '\0');
        }
    }

    /**
     * يضبط مجال الـ Spinner ويربط مُحرِّره بقيمته.
     *
     * <p>الربط ضروري لا تجميلي: بدونه لا تصل الكتابة اليدوية في الحقل إلى قيمة الـ Spinner
     * إلا بعد الضغط على أحد السهمين، فيكتب المستخدم "04" ويحفظ فتبقى النسخة على الساعة 2.
     * ويرفض المُنقِّح النص الفارغ حتى لا تصبح القيمة {@code null}.</p>
     */
    private void configureSpinner(Spinner<Integer> spinner, int min, int max, int initial, boolean wrapAround) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial);
        factory.setWrapAround(wrapAround);
        spinner.setValueFactory(factory);

        TextFormatter<Integer> formatter = new TextFormatter<>(factory.getConverter(), initial,
                change -> change.getControlNewText().matches("[0-9]+") ? change : null);
        spinner.getEditor().setTextFormatter(formatter);
        factory.valueProperty().bindBidirectional(formatter.valueProperty());

        factory.valueProperty().addListener((observable, oldValue, newValue) -> scheduleFieldChanged());
    }

    /**
     * تعديل في حقول الجدولة: يُحدَّث عرض الموعد القادم، ويُنبَّه المستخدم إلى أن التعديل
     * لم يُحفظ بعد.
     *
     * <p>التنبيه ضروري لأن تبويبَي اللغة والطباعة في الشاشة نفسها يُحفظان لحظة الاختيار،
     * فيسهل الظن أن الجدولة كذلك ثم إغلاق الشاشة قبل الضغط على "حفظ الإعدادات".</p>
     */
    private void scheduleFieldChanged() {
        updateNextRunPreview();
        if (!loadingSchedule) {
            statusLabel.setText(I18n.get("settings.scheduleUnsaved"));
        }
    }

    /** يوم الأسبوع يخصّ التكرار الأسبوعي وحده، ويوم الشهر الشهريَّ وحده */
    private void showFieldsFor(BackupFrequency frequency) {
        setShown(frequency == BackupFrequency.WEEKLY, backupDayOfWeekLabel, backupDayOfWeekCombo);
        setShown(frequency == BackupFrequency.MONTHLY, backupDayOfMonthLabel, backupDayOfMonthSpinner);
    }

    private void setShown(boolean shown, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }

    /**
     * يعرض الموعد القادم كما سيحسبه المجدوِل.
     * "يومياً 02:00" وحدها لا تجيب سؤال المستخدم الحقيقي: متى تُؤخذ النسخة القادمة فعلاً؟
     */
    private void updateNextRunPreview() {
        BackupSchedule schedule = scheduleFromFields();
        backupNextRunLabel.setText(I18n.format("settings.nextRunAt",
                schedule.describe(), schedule.nextRunAfter(LocalDateTime.now()).format(MOMENT)));
    }

    private BackupSchedule scheduleFromFields() {
        return new BackupSchedule(
                backupFrequencyCombo.getValue() == null
                        ? BackupSchedule.DEFAULT_FREQUENCY : backupFrequencyCombo.getValue(),
                LocalTime.of(spinnerValue(backupHourSpinner, 0, 23, BackupSchedule.DEFAULT_TIME.getHour()),
                        spinnerValue(backupMinuteSpinner, 0, 59, BackupSchedule.DEFAULT_TIME.getMinute())),
                backupDayOfWeekCombo.getValue() == null
                        ? BackupSchedule.DEFAULT_DAY_OF_WEEK : backupDayOfWeekCombo.getValue(),
                spinnerValue(backupDayOfMonthSpinner, 1, 31, BackupSchedule.DEFAULT_DAY_OF_MONTH));
    }

    /**
     * قيمة الـ Spinner مقصورة على مجالها، بعد تثبيت ما كُتب في مُحرِّره.
     *
     * <p><b>التثبيت هو جوهر الدالة.</b> {@code Spinner} لا ينقل ما يُكتب في حقله إلى قيمته
     * إلا عند فقد التركيز أو ضغط Enter، ولا يكفي ربط {@code TextFormatter} به. فمن كتب
     * الساعة ثم ضغط "حفظ الإعدادات" فوراً كان يحفظ القيمة القديمة، ثم تُعاد تعبئة الشاشة
     * من المحفوظ فترجع إلى 2 - فيبدو للمستخدم أن الموعد لا يُحفظ إطلاقاً.</p>
     *
     * <p>والقصر على المجال ضروري بدوره: {@code IntegerSpinnerValueFactory} لا يقصّ ما
     * يُكتب باليد، و"99" في خانة الساعة ترمي {@code DateTimeException} من
     * {@link LocalTime#of} عند بناء الموعد.</p>
     */
    /** عدد النسخ كما هو في الحقل الآن، بحدود {@link BackupRetention} نفسها التي تطبّقها الخدمة */
    private int retentionFromField() {
        return spinnerValue(backupRetentionSpinner,
                BackupRetention.KEEP_ALL, BackupRetention.MAX_COUNT, DEFAULT_RETENTION);
    }

    private int spinnerValue(Spinner<Integer> spinner, int min, int max, int fallback) {
        commitTypedText(spinner, min, max);

        Integer value = spinner.getValue();
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    /**
     * ينقل ما في حقل الـ Spinner إلى قيمته مقصوراً على المجال.
     *
     * <p>لا تتكرّر بلا نهاية رغم أن {@code setValue} يُطلق المستمع الذي يستدعيها: النداء
     * الثاني يجد القيمة مطابقة للنص فلا يغيّر شيئاً.</p>
     */
    private void commitTypedText(Spinner<Integer> spinner, int min, int max) {
        String text = spinner.getEditor().getText();
        if (text == null || !text.matches("[0-9]{1,9}")) {
            return;
        }
        int typed = Math.min(Math.max(Integer.parseInt(text), min), max);
        if (!Integer.valueOf(typed).equals(spinner.getValue())) {
            spinner.getValueFactory().setValue(typed);
        }
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

        directSheetPrintCheck.setSelected(PrintPreferences.printsSheetsDirectly());
        directSheetPrintCheck.selectedProperty().addListener((observable, was, direct) -> {
            PrintPreferences.setPrintsSheetsDirectly(direct);
            statusLabel.setText(I18n.get("settings.printSaved"));
        });

        centerHeaderCheck.setSelected(PrintPreferences.printsCenterHeader());
        centerHeaderCheck.selectedProperty().addListener((observable, was, print) -> {
            PrintPreferences.setPrintsCenterHeader(print);
            statusLabel.setText(I18n.get("settings.printSaved"));
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
        databaseLabel.setText(datasourceUrl);
        dbUserLabel.setText(datasourceUsername);
    }

    /**
     * يعرض حالة القناة <b>المحفوظة</b>: جاهزة، أو ما ينقصها.
     *
     * <p>القناة تُقرأ من الحقول لأنها معروضة أمام المستخدم، أما ما ينقصها فمن الخدمة لأنه
     * يعتمد على المحفوظ فعلاً - ومنه ما ليس في هذه الشاشة أصلاً كوجود مفتاح على الجهاز.
     * والقراءة على خيط خلفي لأنها تمرّ بقاعدة البيانات.</p>
     */
    private void refreshNotificationState(NotificationChannel channel) {
        NotificationChannel selected = channel == null ? NotificationChannel.WHATSAPP_LINK : channel;
        String mode = I18n.get(selected.requiresManualConfirmation()
                ? "settings.channelManual" : "settings.channelAutomatic");
        notificationChannelLabel.setText(I18n.format("settings.channel", selected.getDisplayName(), mode));

        FxAsync.supply(notificationService::channelProblem, problem -> {
            notifStateLabel.setText(problem
                    .map(reason -> I18n.format("settings.notif.notReady", reason))
                    .orElseGet(() -> I18n.format("settings.notif.ready", mode)));
            notifStateLabel.getStyleClass().remove("danger-text");
            if (problem.isPresent()) {
                notifStateLabel.getStyleClass().add("danger-text");
            }
        }, error -> notifStateLabel.setText(FxAsync.messageOf(error)));
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
            loadedLastAutoBackupAt = settings.getLastAutoBackupAt();

            // العملة الغائبة تعني الافتراضية، لا "بلا عملة": قاعدة مُرقّاة لا تحمل قيمة
            // وكل مبالغها بالجنيه، فالقائمة تُظهر ما يُطبع فعلاً على الإيصالات الآن
            loadedCurrency = settings.getCurrency() == null ? Currency.DEFAULT : settings.getCurrency();
            currencyCombo.setValue(loadedCurrency);

            centerNameField.setText(settings.getCenterName());
            centerPhoneField.setText(settings.getCenterPhone());
            backupPathField.setText(settings.getBackupPath());
            autoBackupCheckBox.setSelected(settings.isAutoBackupEnabled());
            ledgerStartDatePicker.setValue(settings.getLedgerStartDate());
            showBackupSchedule(settings);
            showNotificationSettings(settings);

            if (settings.getLogoPath() != null && !settings.getLogoPath().isBlank()) {
                logoPathField.setText(settings.getLogoPath());
                loadLogoImage(settings.getLogoPath());
            }
            statusLabel.setText("");
        }, error -> Dialogs.error(I18n.format("settings.loadFailed", FxAsync.messageOf(error))));
    }

    /**
     * يملأ حقول الجدولة من الإعدادات المحفوظة.
     * القيم الناقصة تأتي من {@link BackupSchedule} نفسه لا من ثوابت مكرَّرة هنا، حتى لا
     * يعرض البرنامج موعداً ويجدول غيره.
     */
    private void showBackupSchedule(CenterSettings settings) {
        BackupSchedule schedule = BackupSchedule.from(settings);

        // التعبئة من المحفوظ ليست تعديلاً من المستخدم، فلا تُظهر تنبيه "لم يُحفظ بعد"
        loadingSchedule = true;
        try {
            backupFrequencyCombo.setValue(schedule.frequency());
            backupHourSpinner.getValueFactory().setValue(schedule.time().getHour());
            backupMinuteSpinner.getValueFactory().setValue(schedule.time().getMinute());
            backupDayOfWeekCombo.setValue(schedule.dayOfWeek());
            backupDayOfMonthSpinner.getValueFactory().setValue(schedule.dayOfMonth());
            backupRetentionSpinner.getValueFactory().setValue(
                    settings.getBackupRetentionCount() == null
                            ? DEFAULT_RETENTION : settings.getBackupRetentionCount());
        } finally {
            loadingSchedule = false;
        }

        showFieldsFor(schedule.frequency());
        updateNextRunPreview();

        LocalDateTime lastRun = settings.getLastAutoBackupAt();
        // تاريخ قديم أو غائب مع تفعيل النسخ التلقائي = نسخ تفشل ليلة بعد ليلة بلا أن يعلم أحد
        boolean overdue = settings.isAutoBackupEnabled() && schedule.isOverdue(lastRun, LocalDateTime.now());
        backupLastRunLabel.setText(lastRun == null
                ? I18n.get("settings.backupNeverRun")
                : lastRun.format(MOMENT) + (overdue ? " — " + I18n.get("settings.backupOverdue") : ""));
        backupLastRunLabel.getStyleClass().removeAll("danger-text");
        if (overdue && lastRun != null) {
            backupLastRunLabel.getStyleClass().add("danger-text");
        }
    }

    /** يملأ حقول الإشعارات من المحفوظ، ويعيد الشاشة إلى حالة "لا تعديل معلّق" */
    private void showNotificationSettings(CenterSettings settings) {
        NotificationChannel channel = settings.getNotificationChannel() == null
                ? NotificationChannel.WHATSAPP_LINK : settings.getNotificationChannel();

        loadingNotifications = true;
        try {
            notifChannelCombo.setValue(channel);
            notifApiUrlField.setText(orEmpty(settings.getNotificationApiUrl()));
            notifSenderIdField.setText(orEmpty(settings.getNotificationSenderId()));
            notifTemplateNameField.setText(orEmpty(settings.getNotificationTemplateName()));
            notifTemplateLanguageField.setText(settings.getNotificationTemplateLanguage() == null
                    ? NotificationConfig.DEFAULT_TEMPLATE_LANGUAGE
                    : settings.getNotificationTemplateLanguage());
            notifBodyTemplateField.setText(orEmpty(settings.getNotificationBodyTemplate()));
        } finally {
            loadingNotifications = false;
        }

        loadedNotificationChannel = channel;
        notificationFieldsChanged = false;

        showNotificationFieldsFor(channel);
        updateLinkPreview();
        refreshNotificationState(channel);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @FXML
    public void handleReload(ActionEvent event) {
        loadSettings();
    }

    /**
     * إرسال رسالة تجريبية بالقناة المحفوظة.
     *
     * <p>هو ما يحوّل "ضبطت المزوّد" إلى "الرسائل تصل": مفتاح منتهٍ أو قالب غير معتمَد
     * لا يظهر أيٌّ منهما إلا في ردّ المزوّد على رسالة حقيقية، ولو انتظرنا أول دفعة
     * لاكتُشف ذلك أمام قائمة أربعين ولي أمر.</p>
     */
    @FXML
    public void handleTestNotification(ActionEvent event) {
        // الخدمة تقرأ المحفوظ لا الحقول: اختبار ضبطٍ غير الذي يراه المستخدم يضلّل
        if (notificationFieldsChanged) {
            Dialogs.warning(I18n.get("settings.notif.saveFirst"));
            return;
        }

        String phone = notifTestPhoneField.getText();
        if (phone == null || phone.isBlank()) {
            Dialogs.warning(I18n.get("settings.notif.testPhoneRequired"));
            return;
        }

        // رقم حقيقي وشخص حقيقي: التأكيد يذكر الرقم كما كُتب
        if (!Dialogs.confirm(I18n.get("settings.notif.testConfirmTitle"),
                I18n.format("settings.notif.testConfirm", phone.trim()))) {
            return;
        }

        statusLabel.setText(I18n.get("settings.notif.testSending"));
        FxAsync.supply(() -> notificationService.sendTestMessage(phone.trim()), result -> {
            statusLabel.setText("");
            if (result.success()) {
                Dialogs.success(I18n.get("settings.notif.testDone"));
            } else {
                Dialogs.error(I18n.get("settings.notif.testFailed"), result.failureReason());
            }
        }, error -> {
            statusLabel.setText("");
            Dialogs.error(I18n.get("settings.notif.testFailed"), FxAsync.messageOf(error));
        });
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

        Currency currency = currencyCombo.getValue() == null
                ? Currency.DEFAULT : currencyCombo.getValue();

        // تبديل العملة لا يحوّل مبلغاً واحداً مما هو محفوظ: خمسمئة جنيه تصير خمسمئة ريال
        // بالرقم نفسه، في الأرصدة والمستحقات والكشوف المطبوعة معاً. قرار يُسأل عنه صراحةً،
        // لا نتيجة صامتة لاختيار من قائمة - نفس معاملة تاريخ بداية الدفتر فوق
        if (currency != loadedCurrency && !Dialogs.confirm(I18n.get("common.confirm"),
                I18n.format("settings.currencyConfirm",
                        loadedCurrency.getDisplayName(), currency.getDisplayName()))) {
            statusLabel.setText(I18n.get("settings.saveCancelled"));
            return;
        }

        NotificationChannel channel = notifChannelCombo.getValue() == null
                ? NotificationChannel.WHATSAPP_LINK : notifChannelCombo.getValue();

        // التحويل إلى قناة تلقائية يعني أن البرنامج صار يراسل أولياء الأمور بلا أن يرى
        // موظفٌ النصّ ولا الرقم. تغيير في صلاحية الكلام باسم السنتر، لا في شكل شاشة.
        if (!channel.requiresManualConfirmation() && channel != loadedNotificationChannel
                && !Dialogs.confirm(I18n.get("settings.notif.automaticConfirmTitle"),
                I18n.format("settings.notif.automaticConfirm", channel.getDisplayName()))) {
            statusLabel.setText(I18n.get("settings.saveCancelled"));
            return;
        }

        // إعدادات الجهاز تُحفظ قبل إعدادات السنتر: فشلها يوقف الحفظ كله، بينما لو حُفظت
        // بعده لبقيت نسخ مجدولة بلا كلمة مرور تشفير تفشل كل ليلة
        if (!saveEncryptionPreferences() || !saveNotificationPreferences(channel)) {
            return;
        }

        BackupSchedule schedule = scheduleFromFields();
        CenterSettings settings = CenterSettings.builder()
                .centerName(trimmed(centerNameField))
                .centerPhone(trimmed(centerPhoneField))
                .currency(currency)
                .logoPath(trimmed(logoPathField))
                .backupPath(trimmed(backupPathField))
                .autoBackupEnabled(autoBackupCheckBox.isSelected())
                .backupFrequency(schedule.frequency())
                .backupTime(schedule.time())
                .backupDayOfWeek(schedule.dayOfWeek())
                .backupDayOfMonth(schedule.dayOfMonth())
                .backupRetentionCount(retentionFromField())
                .lastAutoBackupAt(loadedLastAutoBackupAt) // الشاشة تعرضه ولا تعدّله
                .ledgerStartDate(ledgerStart)
                .notificationChannel(channel)
                .notificationApiUrl(trimmed(notifApiUrlField))
                .notificationSenderId(trimmed(notifSenderIdField))
                .notificationTemplateName(trimmed(notifTemplateNameField))
                .notificationTemplateLanguage(trimmed(notifTemplateLanguageField))
                .notificationBodyTemplate(trimmed(notifBodyTemplateField))
                .build();

        boolean brandingChanged = !java.util.Objects.equals(loadedCenterName, settings.getCenterName())
                || !java.util.Objects.equals(loadedLogoPath, settings.getLogoPath());

        // العملة معها: الشاشات المفتوحة رسمت مبالغها بالرمز القديم، وإعادة البناء وحدها
        // تجعل ما يراه المستخدم بعد الحفظ مطابقاً لما صار يُطبع على الإيصال
        boolean currencyChanged = currency != loadedCurrency;

        FxAsync.supply(() -> settingsService.save(settings), saved -> {
            statusLabel.setText(I18n.get("settings.saved"));
            showBackupSchedule(saved);
            showNotificationSettings(saved);
            Dialogs.success(I18n.get("settings.savedDetail"));

            // إعادة بناء اللوحة تعيدنا إلى الرئيسية، فلا تُنفَّذ إلا حين تغيّرت الترويسة فعلاً
            if (brandingChanged || currencyChanged) {
                reloadDashboard();
            }
        }, error -> Dialogs.error(I18n.format("settings.saveFailed", FxAsync.messageOf(error))));
    }

    /**
     * يحفظ تفضيلات التشفير على هذا الجهاز.
     *
     * <p>التأكيد يظهر حين <b>يتغيّر</b> التشفير أو كلمته فقط. كان يظهر عند كل حفظ، فمن
     * فعّل التشفير مرة صار كل تعديل لاحق - موعد نسخة، اسم سنتر - يسأله سؤالاً مخيفاً عن
     * كلمة المرور، وضغطة "إلغاء" واحدة كانت تُلغي الحفظ كله بلا أي رسالة تقول ذلك.</p>
     *
     * @return {@code false} إن كان الإدخال غير صالح أو ألغى المستخدم، فلا يُكمَل الحفظ
     */
    private boolean saveEncryptionPreferences() {
        boolean enabled = backupEncryptCheckBox.isSelected();
        String passphrase = backupPassphraseField.getText();
        String confirmation = backupPassphraseConfirmField.getText();
        boolean changed = enabled != loadedEncryptionEnabled
                || !java.util.Objects.equals(passphrase, loadedPassphrase);

        if (enabled) {
            if (passphrase == null || passphrase.length() < MIN_PASSPHRASE_LENGTH) {
                Dialogs.warning(I18n.format("settings.passphraseTooShort", MIN_PASSPHRASE_LENGTH));
                return false;
            }
            if (!passphrase.equals(confirmation)) {
                Dialogs.warning(I18n.get("settings.passphraseMismatch"));
                return false;
            }
            // النسخ المأخوذة بكلمة قديمة لا تُفكّ بالجديدة، وهذا لا يمكن التراجع عنه بعد فقدانها
            if (changed && !Dialogs.confirm(I18n.get("settings.encryptionConfirmTitle"),
                    I18n.get("settings.encryptionConfirm"))) {
                statusLabel.setText(I18n.get("settings.saveCancelled"));
                return false;
            }
        }

        if (!changed) {
            return true; // لا شيء ليُكتب في تفضيلات الجهاز
        }

        try {
            BackupPreferences.set(enabled, enabled ? passphrase.toCharArray() : null);
            loadedEncryptionEnabled = enabled;
            loadedPassphrase = enabled ? passphrase : "";
            return true;
        } catch (RuntimeException e) {
            Dialogs.error(FxAsync.messageOf(e));
            return false;
        }
    }

    /**
     * يحفظ ما يخصّ هذا الجهاز من إعدادات الإشعارات: شكل الرابط ومفتاح المزوّد.
     *
     * <p>القالب المكسور يُرفض هنا لا عند الإرسال: قالب بلا {@code {phone}} أو
     * {@code {text}} يفتح محادثة فارغة أو محادثة بلا نص، ويظنّ الموظف أنه أرسل.</p>
     *
     * @return {@code false} إن كان الإدخال غير صالح، فلا يُكمَل الحفظ
     */
    private boolean saveNotificationPreferences(NotificationChannel channel) {
        WhatsAppLinkStyle style = notifLinkStyleCombo.getValue() == null
                ? WhatsAppLinkStyle.WA_ME : notifLinkStyleCombo.getValue();
        String template = notifLinkTemplateField.getText();

        if (channel == NotificationChannel.WHATSAPP_LINK) {
            try {
                WhatsAppLink.build(style, template, "201000000000", "x");
            } catch (IllegalArgumentException e) {
                Dialogs.warning(I18n.get("settings.notif.linkTemplateInvalid"), e.getMessage());
                return false;
            }
        }

        try {
            NotificationPreferences.setLink(style, template);

            String token = notifTokenField.getText();
            // المفتاح الفارغ يمحو المحفوظ: تركه بعد التحوّل عن المزوّد يبقي سرّاً بلا فائدة
            NotificationPreferences.setApiToken(
                    token == null || token.isBlank() ? null : token.toCharArray());
            return true;
        } catch (RuntimeException e) {
            Dialogs.error(FxAsync.messageOf(e));
            return false;
        }
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

        // النسخة اليدوية تحترم عدد النسخ المضبوط في الشاشة ولو لم يُحفظ بعد
        Integer retention = retentionFromField();

        statusLabel.setText(I18n.get("settings.backupRunning"));
        FxAsync.supply(() -> backupService.executeBackup(backupPath, retention), outcome -> {
            statusLabel.setText("");
            // الرسالة تقول كم نسخة قديمة حُذفت: بدونها لا يعرف المستخدم هل عمل عدد النسخ
            // المحفوظة أصلاً إلا بعدّ الملفات في المجلد بنفسه
            Dialogs.success(outcome.describe());
        }, error -> {
            statusLabel.setText("");
            // الرسالة تحمل الآن سبب الفشل من mysqldump نفسه بدل "فشلت العملية" وحدها
            Dialogs.error(I18n.get("settings.backupFailed"), FxAsync.messageOf(error));
        });
    }

    @FXML
    public void handleRestoreBackup(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("settings.chooseBackupFile"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.get("settings.backupFiles"),
                        "*.sql", "*" + BackupCrypto.ENCRYPTED_SUFFIX),
                new FileChooser.ExtensionFilter(I18n.get("settings.sqlFiles"), "*.sql"));
        if (backupPathField.getText() != null && !backupPathField.getText().isBlank()) {
            File directory = new File(backupPathField.getText());
            if (directory.isDirectory()) {
                fileChooser.setInitialDirectory(directory);
            }
        }

        File selectedFile = fileChooser.showOpenDialog(windowOf(event));
        if (selectedFile == null) {
            return;
        }

        // كلمة المرور تُطلب قبل التأكيد المدمِّر: لا معنى لمحو القاعدة ثم اكتشاف أن الملف لا يُفكّ
        char[] passphrase = null;
        if (BackupCrypto.isEncrypted(selectedFile.toPath())) {
            Optional<String> answer = Dialogs.password(I18n.get("settings.passphraseTitle"),
                    I18n.format("settings.passphraseFor", selectedFile.getName()),
                    storedPassphrase());
            if (answer.isEmpty() || answer.get().isEmpty()) {
                return;
            }
            passphrase = answer.get().toCharArray();
        }

        // عملية مدمّرة لا رجعة فيها: التأكيد يذكر اسم الملف صراحةً
        if (!Dialogs.confirm(I18n.get("settings.restoreConfirmTitle"),
                I18n.format("settings.restoreConfirm", selectedFile.getName()))) {
            return;
        }

        char[] key = passphrase;
        statusLabel.setText(I18n.get("settings.restoreRunning"));
        FxAsync.run(() -> backupService.restoreBackup(selectedFile.getAbsolutePath(), key), () -> {
            statusLabel.setText("");
            Dialogs.success(I18n.get("settings.restoreDone"));
        }, error -> {
            statusLabel.setText("");
            Dialogs.error(I18n.get("settings.restoreFailed"), FxAsync.messageOf(error));
        });
    }

    /** كلمة المرور المحفوظة على الجهاز، لتُملأ مسبقاً في نافذة الاستعادة */
    private String storedPassphrase() {
        char[] saved = BackupPreferences.passphrase();
        if (saved == null) {
            return null;
        }
        String value = new String(saved);
        java.util.Arrays.fill(saved, '\0');
        return value;
    }

    private Window windowOf(ActionEvent event) {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
