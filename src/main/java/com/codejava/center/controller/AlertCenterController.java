package com.codejava.center.controller;

import com.codejava.center.domain.Alert;
import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertCategory;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.alert.AlertSchedule;
import com.codejava.center.service.alert.AlertScanResult;
import com.codejava.center.service.alert.AlertService;
import com.codejava.center.service.dto.AlertPage;
import com.codejava.center.util.AlertPreferences;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.ViewLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * مركز التنبيهات: ما وقع، وضبط ما يقع.
 *
 * <p>التصفية على مرحلتين كما في سجل المراقبة: الفترة والتصنيف والدرجة تُنفَّذ في قاعدة
 * البيانات لأنها تحدّد ما يُجلب أصلاً، أما البحث النصّي و"غير المعالَجة فقط" فيعملان
 * على ما جُلب فيستجيبان مع كل حرف بلا رحلة إلى القاعدة.</p>
 *
 * <p>ضبط القاعدة يقع في نافذة مستقلة تُفتح من زرّ سطرها - {@link AlertRuleEditorController} -
 * لا في خلايا الجدول ولا في لوح بجواره: معنى كل حدّ يختلف باختلاف النوع، ولا بد أن
 * يُشرح بجواره لحظة كتابته.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class AlertCenterController {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AlertService alertService;
    private final ViewLoader viewLoader;

    // مقاس نافذة الضبط: عرضٌ يسع شرحاً طويلاً ومغيِّره في سطر واحد بلا لفّ،
    // وارتفاعٌ يسع أطول قاعدة (ثلاثة مغيِّرات) بلا تمرير. وكلاهما يكبر مع مقاس
    // الواجهة في ViewLoader ويُقصّ عند حدود الشاشة
    private static final String RULE_EDITOR_FXML = "/fxml/AlertRuleEditor.fxml";
    private static final double RULE_EDITOR_WIDTH = 640;
    private static final double RULE_EDITOR_HEIGHT = 660;

    @FXML private Label masterStateLabel;

    // ------------------------------------------------------------- الصندوق
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<AlertCategory> categoryCombo;
    @FXML private ComboBox<AlertSeverity> severityCombo;
    @FXML private TextField searchField;
    @FXML private CheckBox openOnlyCheck;
    @FXML private Button acknowledgeButton;
    @FXML private Label summaryLabel, truncationLabel;

    @FXML private TableView<Alert> alertsTable;
    @FXML private TableColumn<Alert, String> colTime, colSeverity, colType, colTarget, colMessage, colStatus;

    // ------------------------------------------------------------- القواعد
    @FXML private CheckBox alertsEnabledCheck;
    @FXML private Spinner<Integer> scanHourSpinner, scanMinuteSpinner;
    @FXML private Label lastScanLabel;

    @FXML private CheckBox devicePopupCheck, deviceTrayCheck;
    @FXML private ComboBox<AlertSeverity> deviceSeverityCombo;

    @FXML private TableView<AlertRule> rulesTable;
    @FXML private TableColumn<AlertRule, String> colRuleCategory, colRuleType, colRuleState,
            colRuleAudience, colRuleSeverity;
    @FXML private TableColumn<AlertRule, Void> colRuleEdit;

    private final ObservableList<Alert> alerts = FXCollections.observableArrayList();
    private FilteredList<Alert> visible;

    private final ObservableList<AlertRule> rules = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // أسبوع مضى: مدة تكفي لمتابعة يومية دون جلب سجل شهور عند كل فتح
        fromPicker.setValue(LocalDate.now().minusWeeks(1));
        toPicker.setValue(LocalDate.now());

        configureInboxFilters();
        configureInboxColumns();

        visible = new FilteredList<>(alerts, row -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyLocalFilter());
        openOnlyCheck.selectedProperty().addListener((obs, oldVal, newVal) -> applyLocalFilter());
        alertsTable.setItems(visible);
        alertsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        configureRulesTable();
        configureScanSpinners();
        loadDevicePreferences();

        loadAlerts();
        loadRules();
        loadScanSettings();
    }

    // ==================================================================== الصندوق

    private void configureInboxFilters() {
        // العنصر null معناه "الكل"؛ وجوده في القائمة يجعل العودة إليه ممكنة بعد الاختيار
        categoryCombo.getItems().add(null);
        categoryCombo.getItems().addAll(AlertCategory.values());
        showAllForNull(categoryCombo, AlertCategory::getDisplayName);

        severityCombo.getItems().add(null);
        severityCombo.getItems().addAll(AlertSeverity.values());
        showAllForNull(severityCombo, AlertSeverity::getDisplayName);
    }

    /**
     * يجعل العنصر {@code null} يُقرأ "الكل".
     *
     * <p>بخليةٍ مكتوبة لا بـ {@code StringConverter}: خلية العرض الافتراضية تعرض
     * {@code promptText} حين تكون القيمة {@code null} ولا تسأل المحوّل، فيظهر خيار
     * "الكل" - وهو الوضع الابتدائي - فراغاً. وخليتان لا واحدة لأن العقدة الواحدة في
     * JavaFX لا تكون لها أبوان.</p>
     */
    private <T> void showAllForNull(ComboBox<T> combo, Function<T, String> display) {
        combo.setCellFactory(list -> allAwareCell(display));
        combo.setButtonCell(allAwareCell(display));
        combo.setValue(null);
    }

    private <T> ListCell<T> allAwareCell(Function<T, String> display) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? I18n.get("common.all") : display.apply(item)));
            }
        };
    }

    private void configureInboxColumns() {
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getRaisedAt().format(TIMESTAMP)));
        colSeverity.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getSeverity().getDisplayName()));
        colType.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getType().getDisplayName()));
        colTarget.setCellValueFactory(d -> new SimpleStringProperty(
                nullSafe(d.getValue().getEntityLabel())));
        // الجملة تُبنى الآن بلغة الواجهة من النوع والوسائط؛ لا نصّ مترجَم في القاعدة
        colMessage.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().describe()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(statusOf(d.getValue())));

        // الدرجة تُميَّز بلون الصف: تنبيه حرج يجب أن يُرى قبل قراءة سطره
        alertsTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(Alert item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isAcknowledged()) {
                    // المعالَج يعود رمادياً: بقاؤه ملوَّناً يجعل الصندوق يبدو مشتعلاً أبداً
                    setStyle(empty || item == null ? "" : "-fx-background-color: #f8f9fa;");
                    return;
                }
                String color = item.getSeverity().getRowColor();
                setStyle(color.isEmpty() ? "" : "-fx-background-color: " + color + ";");
            }
        });
    }

    private String statusOf(Alert alert) {
        if (!alert.isAcknowledged()) {
            return I18n.get("alerts.status.open");
        }
        return alert.getAcknowledgedBy() == null
                ? I18n.get("alerts.status.done")
                : I18n.format("alerts.status.doneBy", alert.getAcknowledgedBy());
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? I18n.get("common.empty") : value;
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadAlerts();
        loadRules();
        loadScanSettings();
    }

    private void loadAlerts() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();

        if (from == null || to == null) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("audit.selectPeriod"));
            return;
        }
        if (from.isAfter(to)) {
            Dialogs.warning(I18n.get("attReport.invalidPeriodTitle"), I18n.get("attReport.invalidPeriod"));
            return;
        }

        AlertCategory category = categoryCombo.getValue();
        AlertSeverity severity = severityCombo.getValue();

        FxAsync.supply(() -> alertService.search(from, to, category, severity), this::showAlerts,
                error -> Dialogs.error(I18n.format("alerts.loadFailed", FxAsync.messageOf(error))));
    }

    private void showAlerts(AlertPage page) {
        alerts.setAll(page.rows());
        applyLocalFilter();

        // القصّ يُعلَن لا يُخفى: صندوق يعرض خمسمئة من ألفين دون أن يقول ذلك يجعل
        // من ينظر إليه يستنتج أن الباقي لم يقع
        truncationLabel.setText(I18n.format("audit.truncated", page.rows().size(), page.totalMatching()));
        truncationLabel.setVisible(page.isTruncated());
        truncationLabel.setManaged(page.isTruncated());
    }

    private void applyLocalFilter() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean openOnly = openOnlyCheck.isSelected();

        visible.setPredicate(row -> (!openOnly || !row.isAcknowledged()) && matches(row, needle));
        summaryLabel.setText(I18n.format("alerts.summary", visible.size(), alerts.size()));
    }

    private boolean matches(Alert row, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        // بالاسم المعروض والجملة المبنيّة، لا بالقيم المخزَّنة: المستخدم يبحث بما يراه
        return contains(row.getEntityLabel(), needle)
                || contains(row.getType().getDisplayName(), needle)
                || contains(row.describe(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    @FXML
    public void handleAcknowledge(ActionEvent event) {
        List<Alert> selected = new ArrayList<>(alertsTable.getSelectionModel().getSelectedItems());
        List<Long> ids = selected.stream().filter(alert -> !alert.isAcknowledged())
                .map(Alert::getId).toList();

        if (ids.isEmpty()) {
            Dialogs.warning(I18n.get("alerts.selectOpenRows"));
            return;
        }

        acknowledgeButton.setDisable(true);
        FxAsync.supply(() -> alertService.acknowledge(ids), changed -> {
            acknowledgeButton.setDisable(false);
            loadAlerts();
        }, error -> {
            acknowledgeButton.setDisable(false);
            Dialogs.error(FxAsync.messageOf(error));
        });
    }

    /**
     * فحص فوري بطلب المستخدم.
     *
     * <p>يُنبَّه قبله إن كانت هناك قاعدة تراسل أولياء الأمور: الفحص اليدوي قد يُطلق
     * عشرات الرسائل الحقيقية في لحظته، وهو ما لا يتوقعه من ضغط زراً اسمه "فحص".</p>
     */
    @FXML
    public void handleScanNow(ActionEvent event) {
        boolean messagesParents = rules.stream().anyMatch(AlertRule::notifiesParents);

        if (messagesParents && !Dialogs.confirm(I18n.get("alerts.scanConfirmTitle"),
                I18n.get("alerts.scanConfirmParents"))) {
            return;
        }

        FxAsync.supply(alertService::scanNow, result -> {
            showScanResult(result);
            loadAlerts();
            loadScanSettings();
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    private void showScanResult(AlertScanResult result) {
        String summary = I18n.format("alerts.scanDone", result.raised(), result.messaged());

        if (result.hasFailures()) {
            Dialogs.error(I18n.get("notify.partialTitle"),
                    summary + "\n\n" + String.join("\n", result.failures()));
        } else {
            Dialogs.success(I18n.get("alerts.scanDoneTitle"), summary);
        }
    }

    // ==================================================================== القواعد

    private void configureRulesTable() {
        colRuleCategory.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getType().getCategory().getDisplayName()));
        colRuleType.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getType().getDisplayName()));
        colRuleState.setCellValueFactory(d -> new SimpleStringProperty(
                I18n.get(d.getValue().isEnabled() ? "alerts.state.on" : "alerts.state.off")));
        colRuleAudience.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAudience().getDisplayName()));
        colRuleSeverity.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getSeverity().getDisplayName()));
        configureEditColumn();

        rulesTable.setRowFactory(table -> {
            // القاعدة الموقفة تُقرأ رمادية: نصف الشاشة سؤاله "ما الذي يعمل الآن؟"
            TableRow<AlertRule> row = new TableRow<>() {
                @Override
                protected void updateItem(AlertRule item, boolean empty) {
                    super.updateItem(item, empty);
                    setStyle(empty || item == null || item.isEnabled()
                            ? "" : "-fx-background-color: #f1f2f6; -fx-text-fill: #95a5a6;");
                }
            };

            // النقر المزدوج على الصف يفتح ما يفتحه زرّه. على الصف لا على الجدول:
            // معالِج على الجدول كله يستجيب للنقر المزدوج على فراغ أسفل الصفوف أيضاً،
            // فيفتح نافذة القاعدة التي بقيت محدَّدة من قبل
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openRuleEditor(row.getItem());
                }
            });
            return row;
        });

        rulesTable.setItems(rules);
    }

    /**
     * زر الضبط داخل كل صف.
     *
     * <p>الزر في الصف لا في شريط الأدوات، لنفس سبب زر اشتراكات الطالب: ضبط قاعدة يُطلب
     * وأنت تنظر إلى سطرها. واللوح الجانبي حين كان داخل التبويب كان يتبع الصف المحدَّد
     * صامتاً، فيُقرأ "حفظ القاعدة" وكأنه يخصّ ما في الجدول لا ما في اللوح.</p>
     */
    private void configureEditColumn() {
        colRuleEdit.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button(I18n.get("alerts.editRule"));

            {
                editButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 2 10;");
                editButton.setOnAction(event -> openRuleEditor(getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });
    }

    /**
     * نافذة ضبط قاعدة واحدة: مستقلة، وتُفتح بالقاعدة مضبوطة فيها قبل عرضها.
     *
     * <p>القواعد تُعاد قراءتها بعد إغلاقها لا بعد حفظٍ داخلها: النافذة محجوزة
     * ({@code showAndWait}) فلا يعود التنفيذ هنا إلا وقد انتهى صاحبها منها، والحالة
     * والوجهة والدرجة في الجدول لا بد أن تقول ما حُفظ.</p>
     */
    private void openRuleEditor(AlertRule rule) {
        // الضغط على زرّ داخل خلية لا يحدّد صفها: يُحدَّد هنا كي يبقى السطر مميَّزاً
        // بعد إغلاق النافذة، فيعرف من ضبط قاعدة أيّها كان يضبط
        rulesTable.getSelectionModel().select(rule);

        try {
            viewLoader.<AlertRuleEditorController>showModal(RULE_EDITOR_FXML,
                    I18n.format("alerts.ruleEditorTitle", rule.getType().getDisplayName()),
                    rulesTable.getScene().getWindow(),
                    RULE_EDITOR_WIDTH, RULE_EDITOR_HEIGHT,
                    controller -> controller.setRule(rule));
        } catch (Exception e) {
            Dialogs.error(I18n.format("alerts.ruleOpenFailed", FxAsync.messageOf(e)));
            return;
        }
        loadRules();
    }

    private void configureScanSpinners() {
        configureSpinner(scanHourSpinner, 0, 23, AlertSchedule.DEFAULT_TIME.getHour());
        configureSpinner(scanMinuteSpinner, 0, 59, AlertSchedule.DEFAULT_TIME.getMinute());
    }

    private <T> StringConverter<T> converter(Function<T, String> display) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : display.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    /**
     * يضبط مجال الـ Spinner ويربط مُحرِّره بقيمته.
     *
     * <p>الربط ضروري لا تجميلي، وهو نفس ما لزم في شاشة الإعدادات: بدونه لا تصل الكتابة
     * اليدوية إلى قيمة الـ Spinner إلا بعد الضغط على أحد السهمين، فيكتب المستخدم الحدّ
     * الذي يريده ويحفظ فتُحفظ القيمة القديمة. والمُنقِّح يرفض النص الفارغ حتى لا تصير
     * القيمة {@code null}.</p>
     */
    private void configureSpinner(Spinner<Integer> spinner, int min, int max, int initial) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial);
        spinner.setValueFactory(factory);

        TextFormatter<Integer> formatter = new TextFormatter<>(factory.getConverter(), initial,
                change -> change.getControlNewText().matches("[0-9]+") ? change : null);
        spinner.getEditor().setTextFormatter(formatter);
        factory.valueProperty().bindBidirectional(formatter.valueProperty());
    }

    private void loadRules() {
        FxAsync.supply(alertService::getRules, loaded -> {
            AlertType previous = selectedType();

            rules.setAll(loaded);
            masterStateLabel.setText(summaryOfRules(loaded));

            // الاختيار السابق يُستعاد: setAll يمسحه، فيفقد المستخدم موضع القاعدة
            // التي كان يضبطها بعد كل عودة من نافذتها
            loaded.stream().filter(rule -> rule.getType() == previous).findFirst()
                    .ifPresent(rule -> rulesTable.getSelectionModel().select(rule));
        }, error -> Dialogs.error(I18n.format("alerts.loadFailed", FxAsync.messageOf(error))));
    }

    private AlertType selectedType() {
        AlertRule selected = rulesTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getType();
    }

    private String summaryOfRules(List<AlertRule> loaded) {
        long enabled = loaded.stream().filter(AlertRule::isEnabled).count();
        long toParents = loaded.stream().filter(AlertRule::notifiesParents).count();
        return I18n.format("alerts.rulesSummary", enabled, loaded.size(), toParents);
    }

    // ================================================================= الجدولة

    private void loadScanSettings() {
        FxAsync.supply(alertService::getScanSettings, settings -> {
            alertsEnabledCheck.setSelected(settings.enabled());
            scanHourSpinner.getValueFactory().setValue(settings.time().getHour());
            scanMinuteSpinner.getValueFactory().setValue(settings.time().getMinute());

            // آخر فحص قديمٌ هو ما يجعل فشلاً متكرراً مرئياً بدل أن يمرّ بصمت
            lastScanLabel.setText(settings.lastScanAt() == null
                    ? I18n.get("alerts.neverScanned")
                    : I18n.format("alerts.lastScan", settings.lastScanAt().format(TIMESTAMP)));
        }, error -> lastScanLabel.setText(FxAsync.messageOf(error)));
    }

    /**
     * تفضيلات العرض على هذا الجهاز.
     *
     * <p>تُقرأ وتُكتب من {@code java.util.prefs} مباشرةً بلا خيط خلفي ولا خدمة: سجلّ
     * النظام لا قاعدة بيانات، والقراءة منه أسرع من جدولة المهمة التي تقرؤه. وهو أيضاً
     * سبب ألا يكون لها ملف ترحيل Flyway - مثل الطابعة واللغة تماماً.</p>
     */
    private void loadDevicePreferences() {
        deviceSeverityCombo.getItems().setAll(AlertSeverity.values());
        deviceSeverityCombo.setConverter(converter(AlertSeverity::getDisplayName));

        devicePopupCheck.setSelected(AlertPreferences.popupEnabled());
        deviceTrayCheck.setSelected(AlertPreferences.trayEnabled());
        deviceSeverityCombo.setValue(AlertPreferences.minimumSeverity());
    }

    @FXML
    public void handleSaveDevicePrefs(ActionEvent event) {
        try {
            AlertPreferences.save(devicePopupCheck.isSelected(), deviceTrayCheck.isSelected(),
                    deviceSeverityCombo.getValue());
            Dialogs.success(I18n.get("common.updated"));
        } catch (RuntimeException e) {
            Dialogs.error(FxAsync.messageOf(e));
        }
    }

    @FXML
    public void handleSaveSchedule(ActionEvent event) {
        boolean enabled = alertsEnabledCheck.isSelected();
        LocalTime time = LocalTime.of(scanHourSpinner.getValue(), scanMinuteSpinner.getValue());

        FxAsync.run(() -> alertService.saveScanSettings(enabled, time), () -> {
            Dialogs.success(I18n.get("common.updated"));
            loadScanSettings();
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }
}
