package com.codejava.center.controller;

import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.enums.AuditCategory;
import com.codejava.center.service.AuditService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.dto.AuditPage;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * سجل المراقبة: من فعل ماذا ومتى.
 *
 * <p>التصفية على مرحلتين عن قصد. الفترة والمستخدم والتصنيف تُنفَّذ في قاعدة البيانات
 * لأنها تحدّد ما يُجلب أصلاً - والسجل أكبر من أن يُجلب كاملاً - أما البحث النصّي
 * و"الفاشلة فقط" فيعملان على ما جُلب، فيستجيبان مع كل حرف بلا رحلة إلى القاعدة.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class AuditLogController {

    /** الثواني ليست تفصيلاً زائداً: أحداث عملية واحدة تقع في نفس الدقيقة وترتيبها هو معناها */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditService auditService;
    private final ReportService reportService;

    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<String> actorCombo;
    @FXML private ComboBox<AuditCategory> categoryCombo;
    @FXML private TextField searchField;
    @FXML private CheckBox failuresOnlyCheck;
    @FXML private Label summaryLabel, truncationLabel;

    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, String> colTime, colActor, colRole, colAction,
            colTarget, colAmount, colDetails, colStatus;

    private final ObservableList<AuditLog> events = FXCollections.observableArrayList();
    private FilteredList<AuditLog> visible;

    @FXML
    public void initialize() {
        // أسبوع مضى: مدة قصيرة تكفي لمتابعة يومية دون جلب سجل سنة كامل عند كل فتح
        fromPicker.setValue(LocalDate.now().minusWeeks(1));
        toPicker.setValue(LocalDate.now());

        configureFilters();
        configureColumns();

        visible = new FilteredList<>(events, row -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyLocalFilter());
        failuresOnlyCheck.selectedProperty().addListener((obs, oldVal, newVal) -> applyLocalFilter());
        auditTable.setItems(visible);

        loadActors();
        loadEvents();
    }

    private void configureFilters() {
        // العنصر null معناه "الكل"؛ وجوده داخل القائمة يجعل العودة إليه ممكنة
        // بعد اختيار قيمة، وهو ما لا يتيحه ترك القائمة بلا اختيار
        actorCombo.getItems().add(null);
        showAllForNull(actorCombo, actor -> actor);

        categoryCombo.getItems().add(null);
        categoryCombo.getItems().addAll(AuditCategory.values());
        showAllForNull(categoryCombo, AuditCategory::getDisplayName);
    }

    /**
     * يجعل العنصر {@code null} في القائمة يُقرأ "الكل".
     *
     * <p>بخلية مكتوبة لا بـ {@code StringConverter}: خلية العرض الافتراضية تعرض
     * {@code promptText} حين تكون القيمة {@code null} ولا تسأل المحوّل، فكان خيار
     * "الكل" - وهو الوضع الابتدائي للشاشة - يظهر فراغاً.</p>
     *
     * <p>خليتان لا واحدة: خلية الزر وخلايا القائمة المنسدلة، والعقدة الواحدة في
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

    private void configureColumns() {
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getOccurredAt().format(TIMESTAMP)));

        // غياب اسم المستخدم يعني أن النظام هو الفاعل (نسخة احتياطية مجدولة)، لا أنه مجهول
        colActor.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getActorUsername() == null
                        ? I18n.get("audit.systemActor") : d.getValue().getActorUsername()));

        colRole.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getActorRole() == null
                        ? I18n.get("common.none") : d.getValue().getActorRole().getDisplayName()));

        // اسم الحدث يُترجَم عند العرض؛ المخزَّن هو قيمة الـ enum وحدها
        colAction.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAction().getDisplayName()));

        colTarget.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().getEntityLabel())));

        // الأحداث غير المالية بلا مبلغ: خانة فارغة أصدق من صفر
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAmount() == null
                        ? I18n.get("common.empty") : MoneyUtils.format(d.getValue().getAmount())));

        colDetails.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().getDetails())));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(
                I18n.get(d.getValue().isSuccessful() ? "audit.status.ok" : "audit.status.failed")));

        // الرفض والفشل هما ما يُفتح السجل من أجله؛ تمييزهما بلون يجنّب قراءة كل سطر
        auditTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(AuditLog item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(empty || item == null || item.isSuccessful()
                        ? "" : "-fx-background-color: #fdecea;");
            }
        });
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? I18n.get("common.empty") : value;
    }

    private void loadActors() {
        FxAsync.supply(auditService::getActors, actors -> {
            String previous = actorCombo.getValue();

            List<String> items = new ArrayList<>();
            items.add(null); // "الكل"
            items.addAll(actors);
            actorCombo.getItems().setAll(items);

            // الاختيار السابق يُستعاد إن كان ما زال موجوداً: setAll يمسحه وإلا عادت
            // القائمة إلى "الكل" بعد كل تحديث بينما المستخدم يتابع مستخدماً بعينه
            actorCombo.setValue(actors.contains(previous) ? previous : null);
        }, error -> Dialogs.error(I18n.format("audit.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleBuild(ActionEvent event) {
        loadEvents();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadActors();
        loadEvents();
    }

    private void loadEvents() {
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

        String actor = actorCombo.getValue();
        AuditCategory category = categoryCombo.getValue();

        FxAsync.supply(() -> auditService.search(from, to, actor, category), this::showEvents,
                error -> Dialogs.error(I18n.format("audit.loadFailed", FxAsync.messageOf(error))));
    }

    private void showEvents(AuditPage page) {
        events.setAll(page.rows());
        applyLocalFilter();

        // القصّ يُعلَن لا يُخفى: سجل يعرض ألف حدث من عشرة آلاف دون أن يقول ذلك
        // يجعل من يراجعه يستنتج أن ما لم يظهر لم يقع
        truncationLabel.setText(I18n.format("audit.truncated", page.rows().size(), page.totalMatching()));
        truncationLabel.setVisible(page.isTruncated());
        truncationLabel.setManaged(page.isTruncated());
    }

    /** البحث النصّي و"الفاشلة فقط" يعملان على ما جُلب، ويحدّثان الملخّص معاً */
    private void applyLocalFilter() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean failuresOnly = failuresOnlyCheck.isSelected();

        visible.setPredicate(row -> (!failuresOnly || !row.isSuccessful()) && matches(row, needle));
        summaryLabel.setText(I18n.format("audit.summary", visible.size(), events.size()));
    }

    private boolean matches(AuditLog row, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return contains(row.getActorUsername(), needle)
                || contains(row.getEntityLabel(), needle)
                || contains(row.getDetails(), needle)
                // الاسم المعروض لا القيمة المخزَّنة: المستخدم يبحث بما يراه على الشاشة
                || contains(row.getAction().getDisplayName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        List<AuditLog> rows = new ArrayList<>(visible);

        if (rows.isEmpty()) {
            Dialogs.warning(I18n.get("audit.nothingToPrint"));
            return;
        }

        try {
            // الطباعة على خيط الواجهة: شرط PrinterJob في JavaFX
            reportService.printAuditReport(rows, fromPicker.getValue(), toPicker.getValue(),
                    ((Node) event.getSource()).getScene().getWindow());
        } catch (Exception e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
        }
    }
}
