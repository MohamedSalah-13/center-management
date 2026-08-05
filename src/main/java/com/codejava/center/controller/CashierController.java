package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.Forms;
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
    private final ReportService reportService;

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
                return group == null ? "" : I18n.format("cashier.groupWithTeacher",
                        group.getName(), group.getTeacher().getName());
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
                descriptionField.setText(I18n.format("cashier.subscriptionDescription", newVal.getName()));
            }
        });

        // تأمين خانة المبلغ
        Forms.decimalOnly(amountField);
        Forms.focusNextOnEnter(amountField, descriptionField);
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
            schoolLevelLabel.setText(currentStudent.getSchoolLevel() == null
                    ? I18n.get("common.none")
                    : currentStudent.getSchoolLevel().getDisplayName());
            showBalance(result.balance());

            // تعبئة المجموعات المشترك بها
            for (StudentGroup sg : result.activeGroups()) {
                groupsComboBox.getItems().add(sg.getGroup());
            }

            // تفعيل قسم الدفع
            paymentSection.setDisable(false);

            if (groupsComboBox.getItems().isEmpty()) {
                Dialogs.warning(I18n.get("cashier.noGroups"));
            }

        }, error -> {
            resetUI();
            Dialogs.error(I18n.get("common.searchError"), FxAsync.messageOf(error));
            barcodeSearchField.selectAll();
        });
    }

    @FXML
    public void handlePaymentAction(ActionEvent event) {
        if (currentStudent == null) return;

        CourseGroup selectedGroup = groupsComboBox.getValue();
        if (selectedGroup == null) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("cashier.selectGroupFirst"));
            return;
        }

        String amountStr = amountField.getText().trim();
        String description = descriptionField.getText().trim();

        if (amountStr.isEmpty() || description.isEmpty()) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("cashier.amountAndDescriptionRequired"));
            return;
        }

        BigDecimal amount;
        try {
            amount = MoneyUtils.normalize(new BigDecimal(amountStr));
        } catch (NumberFormatException e) {
            Dialogs.error(I18n.get("common.invalidInput"), I18n.get("cashier.amountMustBeNumeric"));
            return;
        }

        Student student = currentStudent;

        // الحفظ وقراءة الرصيد في الخلفية؛ الطباعة تبقى على خيط الواجهة
        // لأن PrinterJob وحوار الطباعة في JavaFX يجب أن يعملا عليه
        FxAsync.supply(() -> {
            transactionService.recordStudentPayment(student, selectedGroup, amount, description);
            return transactionService.getStudentBalance(student.getId());
        }, newBalance -> {
            printReceipt(student, selectedGroup, amount, newBalance, description);
            Dialogs.success(I18n.get("cashier.paymentSuccessTitle"), I18n.format("cashier.paymentSuccess",
                    MoneyUtils.formatWithCurrency(amount), MoneyUtils.formatWithCurrency(newBalance)));
            handleCancelAction(null); // إعادة تعيين الشاشة لاستقبال الطالب التالي
        }, error -> Dialogs.error(I18n.get("common.systemError"), FxAsync.messageOf(error)));
    }

    @FXML
    public void handleCancelAction(ActionEvent event) {
        resetUI();
    }

    /** الإيصال يُبنى في ReportService ليحمل ترويسة السنتر (الاسم والشعار والهاتف) */
    private void printReceipt(Student student, CourseGroup group, BigDecimal amount,
                              BigDecimal newBalance, String description) {
        try {
            reportService.printPaymentReceipt(student.getName(), group.getName(), amount,
                    newBalance, description, paymentSection.getScene().getWindow());
        } catch (Exception e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
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
        String none = I18n.get("common.none");
        studentNameLabel.setText(none);
        schoolLevelLabel.setText(none);
        balanceLabel.setText(none);
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