package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import com.codejava.commons.fx.form.FormUtils;
import com.codejava.commons.fx.validation.InputValidator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class CashierController {

    private final StudentService studentService;
    private final TransactionService transactionService;
    private final EnrollmentService enrollmentService;

    // عناصر البحث
    @FXML private TextField barcodeSearchField;
    @FXML private Label studentNameLabel;
    @FXML private Label schoolLevelLabel;
    @FXML private Label balanceLabel;

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
                amountField.setText(MoneyUtils.format(newVal.getSessionPrice()));
                descriptionField.setText("اشتراك مجموعة: " + newVal.getName());
            }
        });

        // تأمين خانة المبلغ
        InputValidator.makeDecimalOnly(amountField);
        FormUtils.focusNextOnEnter(amountField, descriptionField);
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

        FxAsync.supply(() -> {
            Student student = studentService.findByBarcode(barcode);
            List<StudentGroup> activeGroups = enrollmentService.getActiveGroupsOf(student);
            BigDecimal balance = transactionService.getStudentBalance(student.getId());
            return new SearchResult(student, activeGroups, balance);
        }, result -> {
            // تحديث الواجهة عند النجاح
            currentStudent = result.student();
            studentNameLabel.setText(currentStudent.getName());
            schoolLevelLabel.setText(currentStudent.getSchoolLevel());
            showBalance(result.balance());

            // تعبئة المجموعات المشترك بها
            for (StudentGroup sg : result.activeGroups()) {
                groupsComboBox.getItems().add(sg.getGroup());
            }

            // تفعيل قسم الدفع
            paymentSection.setDisable(false);

            if (groupsComboBox.getItems().isEmpty()) {
                AlertUtils.showWarning("تنبيه", "هذا الطالب غير مشترك في أي مجموعة حالياً.");
            }

        }, error -> {
            resetUI();
            AlertUtils.showError("خطأ في البحث", FxAsync.messageOf(error));
            barcodeSearchField.selectAll();
        });
    }

    @FXML
    public void handlePaymentAction(ActionEvent event) {
        if (currentStudent == null) return;

        CourseGroup selectedGroup = groupsComboBox.getValue();
        if (selectedGroup == null) {
            AlertUtils.showWarning("بيانات ناقصة", "يرجى اختيار المجموعة أولاً.");
            return;
        }

        String amountStr = amountField.getText().trim();
        String description = descriptionField.getText().trim();

        if (amountStr.isEmpty() || description.isEmpty()) {
            AlertUtils.showWarning("بيانات ناقصة", "يرجى التأكد من إدخال المبلغ والبيان.");
            return;
        }

        BigDecimal amount;
        try {
            amount = MoneyUtils.normalize(new BigDecimal(amountStr));
        } catch (NumberFormatException e) {
            AlertUtils.showError("إدخال خاطئ", "يرجى إدخال المبلغ كأرقام صحيحة فقط.");
            return;
        }

        Student student = currentStudent;

        // الحفظ وقراءة الرصيد في الخلفية؛ الطباعة تبقى على خيط الواجهة
        // لأن PrinterJob وحوار الطباعة في JavaFX يجب أن يعملا عليه
        FxAsync.supply(() -> {
            transactionService.recordStudentPayment(student, selectedGroup, amount, description);
            return transactionService.getStudentBalance(student.getId());
        }, newBalance -> {
            printReceipt(student, selectedGroup, amount, description);
            AlertUtils.showSuccess("نجاح العملية",
                    "تم تسجيل مبلغ " + MoneyUtils.formatWithCurrency(amount) + " بنجاح لخزينة السنتر.\n"
                            + "رصيد الطالب الآن: " + MoneyUtils.formatWithCurrency(newBalance));
            handleCancelAction(null); // إعادة تعيين الشاشة لاستقبال الطالب التالي
        }, error -> AlertUtils.showError("خطأ في النظام", FxAsync.messageOf(error)));
    }

    @FXML
    public void handleCancelAction(ActionEvent event) {
        resetUI();
    }

    // أضف هذه الدالة داخل CashierController
    private void printReceipt(Student student, CourseGroup group, BigDecimal amount, String description) {
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
                Label paidAmount = new Label("المبلغ المدفوع: " + MoneyUtils.formatWithCurrency(amount));
                Label desc = new Label("البيان: " + description);

                receipt.getChildren().addAll(title, new javafx.scene.control.Separator(), date, studentName, groupName, paidAmount, desc);

                if (job.printPage(receipt)) {
                    job.endJob();
                }
            }
        }
    }
    /** رصيد سالب يعني متأخرات على الطالب، فيُعرض بالأحمر */
    private void showBalance(BigDecimal balance) {
        balanceLabel.setText(MoneyUtils.formatWithCurrency(balance));
        balanceLabel.setStyle(balance.signum() < 0
                ? "-fx-text-fill: #e74c3c;"
                : "-fx-text-fill: #27ae60;");
    }

    private void resetUI() {
        currentStudent = null;
        barcodeSearchField.clear();
        studentNameLabel.setText("---");
        schoolLevelLabel.setText("---");
        balanceLabel.setText("---");
        balanceLabel.setStyle("");
        groupsComboBox.getItems().clear();
        amountField.clear();
        descriptionField.clear();
        paymentSection.setDisable(true);
        barcodeSearchField.requestFocus();
    }

    // Record مساعد لنقل البيانات بين الـ Threads
    private record SearchResult(Student student, List<StudentGroup> activeGroups, BigDecimal balance) {}
}