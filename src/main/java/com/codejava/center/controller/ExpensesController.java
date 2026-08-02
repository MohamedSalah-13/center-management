package com.codejava.center.controller;

import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import com.codejava.commons.fx.validation.InputValidator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * تسجيل المصروفات النثرية (كهرباء، صيانة، نثريات).
 * recordExpense كانت موجودة في TransactionService بلا أي شاشة تستدعيها.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ExpensesController {

    private final TransactionService transactionService;

    @FXML private TextField amountField;
    @FXML private TextField descriptionField;
    @FXML private DatePicker dayPicker;
    @FXML private Label dailyTotalLabel;

    @FXML private TableView<Transaction> expensesTable;
    @FXML private TableColumn<Transaction, String> colTime, colAmount, colDescription;

    private final ObservableList<Transaction> expenses = FXCollections.observableArrayList();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    @FXML
    public void initialize() {
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTransactionDate().format(timeFormatter)));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
                MoneyUtils.format(d.getValue().getAmount())));
        colDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        expensesTable.setItems(expenses);

        InputValidator.makeDecimalOnly(amountField);

        dayPicker.setValue(LocalDate.now());
        dayPicker.valueProperty().addListener((obs, oldVal, newVal) -> loadExpenses());

        loadExpenses();
    }

    private void loadExpenses() {
        LocalDate day = dayPicker.getValue() != null ? dayPicker.getValue() : LocalDate.now();

        // نعيد استخدام استعلام الحركات النقدية ونرشّح المصروفات فقط؛
        // البيانات هنا ليوم واحد فالترشيح في الذاكرة أرخص من استعلام إضافي
        FxAsync.supply(() -> transactionService.getCashMovements(day).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .toList(), list -> {
            expenses.setAll(list);
            dailyTotalLabel.setText(MoneyUtils.formatWithCurrency(sumOf(list)));
        }, error -> AlertUtils.showError("خطأ", "تعذر تحميل المصروفات: " + FxAsync.messageOf(error)));
    }

    private BigDecimal sumOf(List<Transaction> list) {
        return list.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @FXML
    public void handleSave(ActionEvent event) {
        String amountStr = amountField.getText().trim();
        String description = descriptionField.getText().trim();

        if (amountStr.isEmpty() || description.isEmpty()) {
            AlertUtils.showWarning("بيانات ناقصة", "يرجى إدخال المبلغ والبيان.");
            return;
        }

        BigDecimal amount;
        try {
            amount = MoneyUtils.normalize(new BigDecimal(amountStr));
        } catch (NumberFormatException e) {
            AlertUtils.showError("إدخال خاطئ", "المبلغ يجب أن يكون رقماً.");
            return;
        }

        FxAsync.supply(() -> transactionService.recordExpense(amount, description), saved -> {
            AlertUtils.showSuccess("نجاح", "تم تسجيل مصروف بقيمة " + MoneyUtils.formatWithCurrency(amount));
            handleClear(null);

            // المصروف يُسجَّل بتاريخ اليوم، فلا يظهر في الجدول إن كان المعروض يوماً آخر
            if (LocalDate.now().equals(dayPicker.getValue())) {
                loadExpenses();
            } else {
                dayPicker.setValue(LocalDate.now()); // المستمع يتولى إعادة التحميل
            }
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }

    @FXML
    public void handleClear(ActionEvent event) {
        amountField.clear();
        descriptionField.clear();
        amountField.requestFocus();
    }
}
