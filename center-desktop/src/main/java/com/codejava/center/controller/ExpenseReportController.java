package com.codejava.center.controller;

import com.codejava.center.domain.Transaction;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.Sheets;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * تقرير المصروفات: ما أنفقه السنتر خلال شهر أو خلال فترة من تاريخ إلى تاريخ.
 *
 * <p>غير شاشة المصروفات: تلك مكان <b>تسجيل</b> المصروف ولا تعرض إلا يوماً واحداً، وهذه
 * تقرأ ما سُجّل على امتداد فترة وتُخرجه ورقةً. والسؤالان مختلفان: الأول لمن يُمسك الدرج
 * الآن، والثاني لمن يجمع مصروفات الشهر ليقارنها بإيراده.</p>
 *
 * <p>اختيار الشهر والتاريخان ليسا وضعين متنافسين: قائمة الشهور اختصار يكتب في خانتَي
 * التاريخ، والتاريخان وحدهما هما ما يُسأل عنه في قاعدة البيانات. مصدرٌ واحد للفترة يعني
 * أن ما يُطبع لا يختلف عمّا يُقرأ مهما اختار المستخدم.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ExpenseReportController {

    /** الشهور المعروضة في قائمة الاختصار: سنتان تكفيان مقارنةَ شهرٍ بنظيره من العام الماضي */
    private static final int MONTHS_OFFERED = 24;

    private final TransactionService transactionService;
    private final ReportService reportService;

    @FXML private ComboBox<YearMonth> monthCombo;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private TextField searchField;
    @FXML private Button printButton;
    @FXML private Label summaryLabel;

    @FXML private TableView<Transaction> expensesTable;
    @FXML private TableColumn<Transaction, String> colDate, colTime, colDescription, colAmount;

    private final ObservableList<Transaction> expenses = FXCollections.observableArrayList();
    private final FilteredList<Transaction> visible = new FilteredList<>(expenses, row -> true);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    /**
     * يمنع تلاحق المستمعين: كتابة الشهر في خانتَي التاريخ تُطلق مستمعَيهما، ولولا الحارس
     * لَمسحا الشهر الذي كتبهما لتوّه - وهو نفس سبب الحارس في {@code LanguageSelector}.
     */
    private boolean syncing;

    @FXML
    public void initialize() {
        setupMonthCombo();
        setupTable();

        // الشهر الجاري افتراضاً: هو ما يُسأل عنه هذا التقرير أكثر من غيره
        YearMonth thisMonth = YearMonth.now();
        syncing = true;
        monthCombo.setValue(thisMonth);
        fromPicker.setValue(thisMonth.atDay(1));
        toPicker.setValue(thisMonth.atEndOfMonth());
        syncing = false;

        monthCombo.valueProperty().addListener((obs, was, is) -> applyMonth(is));
        fromPicker.valueProperty().addListener((obs, was, is) -> forgetMonthIfEdited());
        toPicker.valueProperty().addListener((obs, was, is) -> forgetMonthIfEdited());
        searchField.textProperty().addListener((obs, was, is) -> applySearch());

        handleGenerate(null);
    }

    private void setupMonthCombo() {
        List<YearMonth> months = new ArrayList<>();
        months.add(null); // "فترة مخصصة": الخانتان وحدهما تحدّدانها
        YearMonth month = YearMonth.now();
        for (int i = 0; i < MONTHS_OFFERED; i++) {
            months.add(month.minusMonths(i));
        }
        monthCombo.getItems().setAll(months);

        // خليتان لا واحدة - خلية الزر وخلايا القائمة - والعقدة الواحدة لا تكون لها أبوان.
        // وبخلية مكتوبة لا StringConverter: الخلية الافتراضية تعرض promptText عند القيمة
        // null ولا تسأل المحوّل، فيظهر خيار "فترة مخصصة" فراغاً
        monthCombo.setCellFactory(list -> monthCell());
        monthCombo.setButtonCell(monthCell());
    }

    private ListCell<YearMonth> monthCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(YearMonth item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? I18n.get("expenseReport.customPeriod") : label(item)));
            }
        };
    }

    /** اسم الشهر بلغة الواجهة وسنته: "أغسطس 2026" - لا نصّ مترجَم فيه، فلا مفتاح له */
    private String label(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, I18n.current()) + " " + month.getYear();
    }

    private void setupTable() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getTransactionDate().toLocalDate())));
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTransactionDate().format(timeFormatter)));
        colDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
                MoneyUtils.format(d.getValue().getAmount())));

        expensesTable.setItems(visible);
        expensesTable.setPlaceholder(new Label(I18n.get("common.noData")));
    }

    /** اختيار شهر يكتب حدّيه في الخانتين ويعرض التقرير فوراً؛ و"فترة مخصصة" لا تكتب شيئاً */
    private void applyMonth(YearMonth month) {
        if (syncing || month == null) {
            return;
        }
        syncing = true;
        fromPicker.setValue(month.atDay(1));
        toPicker.setValue(month.atEndOfMonth());
        syncing = false;

        handleGenerate(null);
    }

    /**
     * تعديل تاريخ بعد اختيار شهر يعني أن الفترة لم تعد ذلك الشهر.
     * إبقاء اسمه في القائمة يجعل الشاشة تقول "أغسطس" وهي تعرض نصفه.
     */
    private void forgetMonthIfEdited() {
        if (syncing || monthCombo.getValue() == null) {
            return;
        }
        syncing = true;
        monthCombo.setValue(null);
        syncing = false;
    }

    @FXML
    public void handleGenerate(ActionEvent event) {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();

        if (from == null || to == null) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("expenseReport.selectPeriod"));
            return;
        }
        if (from.isAfter(to)) {
            Dialogs.warning(I18n.get("attReport.invalidPeriodTitle"), I18n.get("attReport.invalidPeriod"));
            return;
        }

        FxAsync.supply(() -> transactionService.getExpenses(from, to), loaded -> {
            expenses.setAll(loaded);
            applySearch();
        }, error -> Dialogs.error(I18n.format("expenseReport.loadFailed", FxAsync.messageOf(error))));
    }

    /**
     * البحث في البيان يعمل على ما جُلب، والفترة وحدها تذهب إلى قاعدة البيانات.
     * الفصل مقصود: الفترة تحدّ حجم ما يُقرأ أصلاً، أما البحث فيُكتب حرفاً حرفاً ولا يصحّ
     * أن يرسل استعلاماً مع كل حرف.
     */
    private void applySearch() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        visible.setPredicate(row -> needle.isEmpty()
                || (row.getDescription() != null && row.getDescription().toLowerCase().contains(needle)));

        updateSummary();
        printButton.setDisable(visible.isEmpty());
    }

    /**
     * الإجماليات محسوبة على المعروض لا على المقروء: من يبحث عن "كهرباء" ثم يقرأ إجمالياً
     * يشمل كل المصروفات ينسب مصروفات الشهر كلها إلى فاتورة الكهرباء.
     */
    private void updateSummary() {
        BigDecimal total = totalOfVisible();
        BigDecimal largest = visible.stream()
                .map(Transaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(MoneyUtils.ZERO);

        summaryLabel.setText(I18n.format("expenseReport.summary", visible.size(),
                MoneyUtils.formatWithCurrency(total), MoneyUtils.formatWithCurrency(largest)));
    }

    private BigDecimal totalOfVisible() {
        return MoneyUtils.normalize(visible.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        if (visible.isEmpty()) {
            Dialogs.warning(I18n.get("expenseReport.nothingToPrint"));
            return;
        }

        List<Transaction> printed = List.copyOf(visible);
        BigDecimal total = totalOfVisible();
        String description = filterDescription();

        FxAsync.supply(() -> reportService.deliverExpenseReport(printed, total, description),
                Sheets::show,
                error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
    }

    /** وصف التصفية يُطبع على الورقة: البحث جزء منه، وإلا قُرئت نتيجته مصروفاتِ الفترة كلها */
    private String filterDescription() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim();
        return I18n.format("expenseReport.filterDescription",
                fromPicker.getValue(), toPicker.getValue(),
                needle.isEmpty() ? I18n.get("common.all") : needle);
    }
}
