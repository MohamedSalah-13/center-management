package com.codejava.center.controller;

import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.util.List;

/**
 * جرد الخزينة وتقفيل الوردية.
 * calculateTodayNetBalance كانت تغذّي رقماً واحداً في لوحة القيادة فقط،
 * بلا شاشة تُظهر مكوّنات الصافي أو تسمح بطباعة الجرد.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ShiftClosingController {

    private final TransactionService transactionService;
    private final ReportService reportService;

    @FXML private DatePicker dayPicker;
    @FXML private Label incomeLabel, expenseLabel, payoutLabel, netLabel;

    @FXML private TableView<Transaction> movementsTable;
    @FXML private TableColumn<Transaction, String> colTime, colType, colAmount, colStudent, colDescription;

    private final ObservableList<Transaction> movements = FXCollections.observableArrayList();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    private ShiftSummary currentSummary;

    @FXML
    public void initialize() {
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTransactionDate().format(timeFormatter)));
        colType.setCellValueFactory(d -> new SimpleStringProperty(typeLabel(d.getValue().getType())));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
                MoneyUtils.format(d.getValue().getAmount())));
        colStudent.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStudent() != null ? d.getValue().getStudent().getName() : "---"));
        colDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        movementsTable.setItems(movements);

        dayPicker.setValue(LocalDate.now());
        dayPicker.valueProperty().addListener((obs, oldVal, newVal) -> loadShift());

        loadShift();
    }

    private String typeLabel(TransactionType type) {
        return switch (type) {
            case INCOME -> "وارد (اشتراك)";
            case EXPENSE -> "مصروف";
            case TEACHER_PAYOUT -> "مستحقات معلم";
            case SESSION_CHARGE -> "رسوم حصة";
        };
    }

    private void loadShift() {
        LocalDate day = dayPicker.getValue() != null ? dayPicker.getValue() : LocalDate.now();

        FxAsync.supply(() -> new ShiftData(
                transactionService.getShiftSummary(day),
                transactionService.getCashMovements(day)
        ), data -> {
            currentSummary = data.summary();
            incomeLabel.setText(MoneyUtils.formatWithCurrency(data.summary().totalIncome()));
            expenseLabel.setText(MoneyUtils.formatWithCurrency(data.summary().totalExpense()));
            payoutLabel.setText(MoneyUtils.formatWithCurrency(data.summary().totalTeacherPayouts()));
            netLabel.setText(MoneyUtils.formatWithCurrency(data.summary().net()));
            movements.setAll(data.movements());
        }, error -> AlertUtils.showError("خطأ", "تعذر تحميل جرد الوردية: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        if (currentSummary == null) {
            AlertUtils.showWarning("تنبيه", "لم يتم تحميل بيانات الوردية بعد.");
            return;
        }

        LocalDate day = dayPicker.getValue() != null ? dayPicker.getValue() : LocalDate.now();

        try {
            // الطباعة تبقى على خيط الواجهة: PrinterJob في JavaFX يتطلب ذلك
            reportService.printShiftSummary(
                    day, currentSummary, movements,
                    ((Node) event.getSource()).getScene().getWindow());
        } catch (Exception e) {
            AlertUtils.showError("خطأ في الطباعة", FxAsync.messageOf(e));
        }
    }

    private record ShiftData(ShiftSummary summary, List<Transaction> movements) {
    }
}
