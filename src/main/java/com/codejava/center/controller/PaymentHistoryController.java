package com.codejava.center.controller;

import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
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
                String.valueOf(data.getValue().getAmount())
        ));

        colGroup.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getGroup() != null ? data.getValue().getGroup().getName() : "---"
        ));

        colSession.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSession() != null ? data.getValue().getSession().getSessionDate().format(sessionDateFormatter) : "---"
        ));

        colDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription()
        ));

        historyTable.setItems(transactionsList);

        // إعطاء التركيز لحقل البحث تلقائياً
        Platform.runLater(() -> barcodeSearchField.requestFocus());
    }

    @FXML
    public void handleSearchAction(ActionEvent event) {
        String barcode = barcodeSearchField.getText().trim();
        if (barcode.isEmpty()) return;

        transactionsList.clear();
        studentNameLabel.setText("جاري البحث...");
        totalPaidLabel.setText("");

        CompletableFuture.supplyAsync(() -> {
            try {
                Student student = studentService.findByBarcode(barcode);
                List<Transaction> history = transactionService.getStudentTransactions(student.getId());
                return new SearchResult(student, history);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            // عرض بيانات الطالب
            studentNameLabel.setText("اسم الطالب: " + result.student().getName());

            // حساب الإجمالي
            double total = result.history().stream().mapToDouble(Transaction::getAmount).sum();
            totalPaidLabel.setText("إجمالي المدفوعات: " + total + " ج.م");

            // تعبئة الجدول
            transactionsList.setAll(result.history());

            if (result.history().isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "نتيجة البحث", "لا توجد مدفوعات سابقة مسجلة لهذا الطالب.");
            }

            barcodeSearchField.selectAll();

        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                studentNameLabel.setText("اسم الطالب: ---");
                totalPaidLabel.setText("إجمالي المدفوعات: 0 ج.م");
                showAlert(Alert.AlertType.ERROR, "خطأ في البحث", "لم يتم العثور على طالب بهذا الباركود.");
                barcodeSearchField.selectAll();
            });
            return null;
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Record لنقل البيانات بين الـ Threads
    private record SearchResult(Student student, List<Transaction> history) {}
}