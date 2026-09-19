package com.codejava.center.controller;

import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import javafx.application.Platform;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final StudentService studentService;
    private final TransactionService transactionService;

    @FXML private TextField barcodeSearchField;
    @FXML private Label studentNameLabel;
    @FXML private Label totalPaidLabel;

    @FXML private TableView<Transaction> historyTable;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colAmount;
    @FXML private TableColumn<Transaction, String> colGroup;
    @FXML private TableColumn<Transaction, String> colSession;
    @FXML private TableColumn<Transaction, String> colDescription;

    private final ObservableList<Transaction> transactionsList = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private final DateTimeFormatter sessionDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        // إعداد أعمدة الجدول
        colDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTransactionDate().format(dateFormatter)
        ));

        colAmount.setCellValueFactory(data -> new SimpleStringProperty(
                MoneyUtils.format(data.getValue().getAmount())
        ));

        colGroup.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getGroup() != null
                        ? data.getValue().getGroup().getName() : I18n.get("common.none")
        ));

        colSession.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSession() != null
                        ? data.getValue().getSession().getSessionDate().format(sessionDateFormatter)
                        : I18n.get("common.none")
        ));

        colDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription()
        ));

        historyTable.setItems(transactionsList);

        // نصوص الترويسة تُبنى هنا لا في FXML لأنها تدمج قيمة متغيّرة مع نص مترجَم
        showEmptyHeader();

        // إعطاء التركيز لحقل البحث تلقائياً
        Platform.runLater(() -> barcodeSearchField.requestFocus());
    }

    @FXML
    public void handleSearchAction(ActionEvent event) {
        String barcode = barcodeSearchField.getText().trim();
        if (barcode.isEmpty()) return;

        transactionsList.clear();
        studentNameLabel.setText(I18n.get("payment.searching"));
        totalPaidLabel.setText("");

        FxAsync.supply(() -> {
            Student student = studentService.findByBarcode(barcode);
            List<Transaction> history = transactionService.getStudentTransactions(student.getId());
            return new SearchResult(student, history);
        }, result -> {
            studentNameLabel.setText(I18n.format("payment.studentName", result.student().getName()));

            BigDecimal total = result.history().stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPaidLabel.setText(I18n.format("payment.totalPaid", MoneyUtils.formatWithCurrency(total)));

            transactionsList.setAll(result.history());

            if (result.history().isEmpty()) {
                Dialogs.info(I18n.get("payment.searchResult"), I18n.get("payment.noHistory"));
            }

            barcodeSearchField.selectAll();
        }, error -> {
            showEmptyHeader();
            Dialogs.error(I18n.get("common.searchError"), I18n.get("payment.notFound"));
            barcodeSearchField.selectAll();
        });
    }

    private void showEmptyHeader() {
        studentNameLabel.setText(I18n.format("payment.studentName", I18n.get("common.none")));
        totalPaidLabel.setText(I18n.format("payment.totalPaid",
                MoneyUtils.formatWithCurrency(BigDecimal.ZERO)));
    }

    // Record لنقل البيانات بين الـ Threads
    private record SearchResult(Student student, List<Transaction> history) {}
}
