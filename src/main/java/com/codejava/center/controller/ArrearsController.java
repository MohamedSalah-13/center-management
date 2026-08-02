package com.codejava.center.controller;

import com.codejava.center.service.ReportService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/**
 * تقرير المتأخرات: من عليه مستحقات ولماذا تمنعه البوابة.
 * يعرض هاتف ولي الأمر لأنه المدخل الطبيعي لإشعار الأهالي لاحقاً.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ArrearsController {

    private final StudentService studentService;
    private final ReportService reportService;

    @FXML private TextField searchField;
    @FXML private Label countLabel, totalDueLabel;
    @FXML private TableView<StudentBalance> arrearsTable;
    @FXML private TableColumn<StudentBalance, String> colName, colBarcode, colPhone, colParentPhone, colDue;

    private final ObservableList<StudentBalance> arrears = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));
        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().barcode())));
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().phone())));
        colParentPhone.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().parentPhone())));
        colDue.setCellValueFactory(d -> new SimpleStringProperty(MoneyUtils.format(d.getValue().amountDue())));

        FilteredList<StudentBalance> filtered = new FilteredList<>(arrears, b -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                filtered.setPredicate(row -> matches(row, newVal)));

        SortedList<StudentBalance> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(arrearsTable.comparatorProperty());
        arrearsTable.setItems(sorted);

        loadArrears();
    }

    private String nullSafe(String value) {
        return value == null ? "---" : value;
    }

    private boolean matches(StudentBalance row, String filter) {
        if (filter == null || filter.isBlank()) return true;
        String needle = filter.toLowerCase();
        return contains(row.studentName(), needle)
                || contains(row.barcode(), needle)
                || contains(row.phone(), needle)
                || contains(row.parentPhone(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private void loadArrears() {
        FxAsync.supply(studentService::getStudentsInArrears, list -> {
            arrears.setAll(list);
            countLabel.setText(String.valueOf(list.size()));
            totalDueLabel.setText(MoneyUtils.formatWithCurrency(totalDue(list)));
        }, error -> AlertUtils.showError("خطأ", "تعذر تحميل تقرير المتأخرات: " + FxAsync.messageOf(error)));
    }

    private BigDecimal totalDue(List<StudentBalance> list) {
        return list.stream().map(StudentBalance::amountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadArrears();
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        if (arrears.isEmpty()) {
            AlertUtils.showWarning("تنبيه", "لا توجد متأخرات لطباعتها.");
            return;
        }

        try {
            // الطباعة على خيط الواجهة: شرط PrinterJob في JavaFX
            reportService.printArrearsReport(arrears, totalDue(arrears),
                    ((Node) event.getSource()).getScene().getWindow());
        } catch (Exception e) {
            AlertUtils.showError("خطأ في الطباعة", FxAsync.messageOf(e));
        }
    }
}
