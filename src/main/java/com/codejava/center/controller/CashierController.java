package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.InputValidator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class CashierController {

    private final StudentService studentService;
    private final TransactionService transactionService;
    private final StudentGroupRepository studentGroupRepository;

    // عناصر البحث
    @FXML private TextField barcodeSearchField;
    @FXML private Label studentNameLabel;
    @FXML private Label schoolLevelLabel;

    // عناصر الدفع
    @FXML private VBox paymentSection;
    @FXML private ComboBox<CourseGroup> groupsComboBox;
    @FXML private TextField amountField;
    @FXML private TextField descriptionField;

    // الطالب الحالي الذي يتم التعامل معه
    private Student currentStudent = null;

    @FXML
    public void initialize() {
        // تهيئة الـ ComboBox لعرض اسم المجموعة فقط بدلاً من كائن (Object)
        groupsComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? "" : group.getName() + " (المعلم: " + group.getTeacher().getName() + ")";
            }

            @Override
            public CourseGroup fromString(String string) {
                return null;
            }
        });

        // مستمع (Listener) لتغيير المبلغ التلقائي عند اختيار مجموعة
        groupsComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                amountField.setText(String.valueOf(newVal.getSessionPrice()));
                descriptionField.setText("اشتراك مجموعة: " + newVal.getName());
            }
        });

        // تأمين خانة المبلغ
        InputValidator.makeDecimalOnly(amountField);
        // إعطاء التركيز لحقل البحث عند فتح الشاشة
        Platform.runLater(() -> barcodeSearchField.requestFocus());
    }

    @FXML
    public void handleSearchAction(ActionEvent event) {
        String barcode = barcodeSearchField.getText().trim();
        if (barcode.isEmpty()) return;

        // تعطيل قسم الدفع مؤقتاً أثناء البحث
        paymentSection.setDisable(true);
        groupsComboBox.getItems().clear();
        currentStudent = null;

        CompletableFuture.supplyAsync(() -> {
            try {
                Student student = studentService.findByBarcode(barcode);
                List<StudentGroup> activeGroups = studentGroupRepository.findByStudentAndIsActiveTrue(student);
                return new SearchResult(student, activeGroups);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            // تحديث الواجهة عند النجاح
            currentStudent = result.student;
            studentNameLabel.setText(currentStudent.getName());
            schoolLevelLabel.setText(currentStudent.getSchoolLevel());

            // تعبئة المجموعات المشترك بها
            for (StudentGroup sg : result.activeGroups) {
                groupsComboBox.getItems().add(sg.getGroup());
            }

            // تفعيل قسم الدفع
            paymentSection.setDisable(false);

            if (groupsComboBox.getItems().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "هذا الطالب غير مشترك في أي مجموعة حالياً.");
            }

        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                resetUI();
                showAlert(Alert.AlertType.ERROR, "خطأ في البحث", "لم يتم العثور على طالب بهذا الباركود.");
                barcodeSearchField.selectAll();
            });
            return null;
        });
    }

    @FXML
    public void handlePaymentAction(ActionEvent event) {
        if (currentStudent == null) return;

        CourseGroup selectedGroup = groupsComboBox.getValue();
        if (selectedGroup == null) {
            showAlert(Alert.AlertType.WARNING, "بيانات ناقصة", "يرجى اختيار المجموعة أولاً.");
            return;
        }

        String amountStr = amountField.getText().trim();
        String description = descriptionField.getText().trim();

        if (amountStr.isEmpty() || description.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "بيانات ناقصة", "يرجى التأكد من إدخال المبلغ والبيان.");
            return;
        }

        try {
            Double amount = Double.parseDouble(amountStr);

            // استدعاء السيرفيس المالي لحفظ العملية كـ INCOME
            transactionService.recordStudentPayment(currentStudent, selectedGroup, amount, description);

            // -- إضافة استدعاء الطباعة هنا --
            printReceipt(currentStudent, selectedGroup, amount, description);

            showAlert(Alert.AlertType.INFORMATION, "نجاح العملية", "تم تسجيل مبلغ " + amount + " ج.م بنجاح لخزينة السنتر.");
            handleCancelAction(null); // إعادة تعيين الشاشة لاستقبال الطالب التالي

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "إدخال خاطئ", "يرجى إدخال المبلغ كأرقام صحيحة فقط.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ في النظام", e.getMessage());
        }
    }

    @FXML
    public void handleCancelAction(ActionEvent event) {
        resetUI();
    }

    // أضف هذه الدالة داخل CashierController
    private void printReceipt(Student student, CourseGroup group, Double amount, String description) {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job != null) {
            // يمكنك تخطي إظهار حوار الطباعة للطباعة المباشرة السريعة (Point of Sale)
            // إذا أردت الطباعة المباشرة على الطابعة الافتراضية، احذف شرط showPrintDialog
            boolean doPrint = job.showPrintDialog(paymentSection.getScene().getWindow());

            if (doPrint) {
                VBox receipt = new VBox(10);
                receipt.setStyle("-fx-padding: 20; -fx-background-color: white; -fx-border-color: black; -fx-border-width: 1;");

                Label title = new Label("إيصال استلام نقدية");
                title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

                Label date = new Label("التاريخ: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                Label studentName = new Label("الطالب: " + student.getName());
                Label groupName = new Label("المجموعة: " + group.getName());
                Label paidAmount = new Label("المبلغ المدفوع: " + amount + " ج.م");
                Label desc = new Label("البيان: " + description);

                receipt.getChildren().addAll(title, new javafx.scene.control.Separator(), date, studentName, groupName, paidAmount, desc);

                if (job.printPage(receipt)) {
                    job.endJob();
                }
            }
        }
    }
    private void resetUI() {
        currentStudent = null;
        barcodeSearchField.clear();
        studentNameLabel.setText("---");
        schoolLevelLabel.setText("---");
        groupsComboBox.getItems().clear();
        amountField.clear();
        descriptionField.clear();
        paymentSection.setDisable(true);
        barcodeSearchField.requestFocus();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Record مساعد لنقل البيانات بين الـ Threads
    private record SearchResult(Student student, List<StudentGroup> activeGroups) {}
}