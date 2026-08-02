package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.util.FxAsync;
import com.codejava.commons.fx.dialog.AlertUtils;
import com.codejava.commons.fx.form.FormUtils;
import com.codejava.commons.fx.validation.InputValidator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class StudentRegistrationController {

    private final StudentService studentService;
    private final ReportService reportService;
    // إضافة الخدمات الجديدة
    private final CourseGroupService courseGroupService;
    private final EnrollmentService enrollmentService;

    @FXML private TextField nameField, phoneField, parentPhoneField, barcodeField;
    @FXML private ComboBox<String> schoolLevelCombo;

    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> colBarcode, colName, colPhone, colLevel;
    @FXML private Button updateButton, deleteButton;

    // عناصر واجهة الاشتراك الجديدة
    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private Label groupCapacityLabel;
    @FXML private Label subscribedGroupsLabel;
    @FXML private Button subscribeButton;
    @FXML private TextField searchField;

    private final ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private Student selectedStudent = null;

    @FXML
    public void initialize() {
        schoolLevelCombo.getItems().addAll("الصف الأول الإعدادي", "الصف الثاني الإعدادي", "الصف الثالث الإعدادي", "الصف الأول الثانوي", "الصف الثاني الثانوي", "الصف الثالث الثانوي");

        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBarcode()));
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        colLevel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSchoolLevel()));

        studentTable.setItems(studentsList);

        setupGroupComboBox();
        setupTableSelectionListener();

        loadStudents();
        loadGroups();
        InputValidator.makeNumericOnly(phoneField, parentPhoneField);
        FormUtils.focusNextOnEnter(nameField, phoneField, parentPhoneField);


        FilteredList<Student> filteredData = new FilteredList<>(studentsList, b -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(student -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                // البحث بالاسم
                if (student.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                // البحث برقم الهاتف
                else if (student.getPhone() != null && student.getPhone().contains(lowerCaseFilter)) {
                    return true;
                }
                // البحث بالباركود
                else if (student.getBarcode() != null && student.getBarcode().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false; // لا يوجد تطابق
            });
        });

        SortedList<Student> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(studentTable.comparatorProperty());
        studentTable.setItems(sortedData);
    }

    private void setupGroupComboBox() {
        // عرض اسم المجموعة في قائمة الاختيار بدلاً من الكائن
        groupComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? "" : group.getName();
            }
            @Override
            public CourseGroup fromString(String string) { return null; }
        });

        // مراقبة التغيير في اختيار المجموعة لعرض السعة
        groupComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                FxAsync.supply(() -> enrollmentService.countActiveMembers(newVal), currentStudents -> {
                    groupCapacityLabel.setText(String.format("السعة: %d / %d", currentStudents, newVal.getMaxCapacity()));

                    // تلوين النص بالأحمر إذا اكتملت السعة
                    if (currentStudents >= newVal.getMaxCapacity()) {
                        groupCapacityLabel.setStyle("-fx-text-fill: #e74c3c;");
                    } else {
                        groupCapacityLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    }
                }, error -> groupCapacityLabel.setText("السعة: تعذر الحساب"));
            } else {
                groupCapacityLabel.setText("السعة: ---");
            }
        });
    }

    private void setupTableSelectionListener() {
        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedStudent = newVal;
                nameField.setText(selectedStudent.getName());
                phoneField.setText(selectedStudent.getPhone());
                parentPhoneField.setText(selectedStudent.getParentPhone());
                schoolLevelCombo.setValue(selectedStudent.getSchoolLevel());
                barcodeField.setText(selectedStudent.getBarcode());

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
                subscribeButton.setDisable(false); // تفعيل زر الاشتراك

                // جلب وعرض المجموعات المشترك بها هذا الطالب
                updateStudentGroupsLabel(selectedStudent);
            } else {
                subscribeButton.setDisable(true);
                subscribedGroupsLabel.setText("المجموعات المشترك بها: لم يتم تحديد طالب");
            }
        });
    }

    private void updateStudentGroupsLabel(Student student) {
        FxAsync.supply(() -> enrollmentService.getActiveGroupsOf(student), groups -> {
            if (groups.isEmpty()) {
                subscribedGroupsLabel.setText("المجموعات المشترك بها: لا يوجد");
            } else {
                String groupNames = groups.stream()
                        .map(sg -> sg.getGroup().getName())
                        .collect(Collectors.joining("، "));
                subscribedGroupsLabel.setText("المجموعات المشترك بها: " + groupNames);
            }
        }, error -> subscribedGroupsLabel.setText("تعذر تحميل المجموعات: " + FxAsync.messageOf(error)));
    }

    private void loadStudents() {
        FxAsync.supply(studentService::getAllStudents,
                students -> studentsList.setAll(students),
                error -> AlertUtils.showError("خطأ", "تعذر تحميل الطلاب: " + FxAsync.messageOf(error)));
    }

    private void loadGroups() {
        FxAsync.supply(courseGroupService::getAllGroups,
                groups -> groupComboBox.getItems().setAll(groups),
                error -> AlertUtils.showError("خطأ", "تعذر تحميل المجموعات: " + FxAsync.messageOf(error)));
    }

    // --- دالة الاشتراك في المجموعة الجديدة ---
    @FXML
    public void handleSubscribeAction(ActionEvent event) {
        if (selectedStudent == null || groupComboBox.getValue() == null) {
            AlertUtils.showWarning("تنبيه", "يرجى تحديد طالب من الجدول واختيار مجموعة للاشتراك.");
            return;
        }

        CourseGroup selectedGroup = groupComboBox.getValue();
        Student target = selectedStudent;

        // فحص السعة والحفظ صارا داخل Transaction واحدة في EnrollmentService،
        // وعدّ السعة بعد النجاح انتقل للخلفية بدل تنفيذه على خيط الواجهة
        FxAsync.supply(() -> {
            enrollmentService.subscribe(target, selectedGroup);
            return enrollmentService.countActiveMembers(selectedGroup);
        }, memberCount -> {
            AlertUtils.showSuccess("نجاح", "تم تسجيل الطالب في المجموعة بنجاح.");
            updateStudentGroupsLabel(target);
            groupCapacityLabel.setText(String.format("السعة: %d / %d", memberCount, selectedGroup.getMaxCapacity()));
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        saveOrUpdateStudent(new Student());
    }

    @FXML
    public void handleUpdateAction(ActionEvent event) {
        if (selectedStudent != null) {
            saveOrUpdateStudent(selectedStudent);
        }
    }

    private void saveOrUpdateStudent(Student student) {
        student.setName(nameField.getText());
        student.setPhone(phoneField.getText());
        student.setParentPhone(parentPhoneField.getText());
        student.setSchoolLevel(schoolLevelCombo.getValue());
        student.setBarcode(barcodeField.getText().isEmpty() ? null : barcodeField.getText());
        student.setActive(true);

        boolean isNew = student.getId() == null;

        // الحفظ في الخلفية: كان يجري على خيط الواجهة فيجمّد الشاشة حتى ترد قاعدة البيانات
        FxAsync.supply(() -> studentService.saveStudent(student), saved -> {
            if (isNew) {
                studentsList.add(saved);
                AlertUtils.showSuccess("نجاح", "تم الحفظ. الباركود: " + saved.getBarcode());
            } else {
                // البحث عن الموضع في القائمة المصدر وليس في العرض (الجدول مربوط بـ SortedList/FilteredList
                // ولذلك يختلف ترتيب صفوفه عن studentsList عند الفرز أو البحث)
                int idx = studentsList.indexOf(student);
                if (idx >= 0) {
                    studentsList.set(idx, saved);
                }
                AlertUtils.showSuccess("نجاح", "تم التعديل بنجاح.");
            }
            handleClearAction(null);
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedStudent == null) return;

        if (AlertUtils.showConfirm("تأكيد الحذف", "حذف الطالب: " + selectedStudent.getName() + "؟")) {
            Student target = selectedStudent;
            FxAsync.run(() -> studentService.deleteStudent(target.getId()), () -> {
                studentsList.remove(target);
                handleClearAction(null);
            }, error -> AlertUtils.showError("خطأ",
                    "لا يمكن حذف الطالب بسبب وجود حركات مالية أو حضور مسجلة له."));
        }
    }

    @FXML
    public void handleClearAction(ActionEvent event) {
        nameField.clear();
        phoneField.clear();
        parentPhoneField.clear();
        schoolLevelCombo.setValue(null);
        barcodeField.clear();

        selectedStudent = null;
        studentTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
        subscribeButton.setDisable(true);
        groupComboBox.setValue(null);
        groupCapacityLabel.setText("السعة: ---");
        subscribedGroupsLabel.setText("المجموعات المشترك بها: لم يتم تحديد طالب");
    }

    @FXML
    public void handleExportIdCards(ActionEvent event) {
        if (studentsList.isEmpty()) {
            AlertUtils.showWarning("تنبيه", "لا يوجد طلاب في الجدول لتصدير كارنيهاتهم.");
            return;
        }

        // تجهيز مسار الحفظ (مثلاً سطح المكتب) بملف يحمل تاريخ اليوم
        String fileName = "Student_ID_Cards_" + java.time.LocalDate.now().toString();

        CompletableFuture.supplyAsync(() -> {
            try {
                // استدعاء دالة التصدير الموجودة مسبقاً في ReportService
                // يتم تمرير studentsList كمصدر بيانات (DataSource) بدلاً من استعلام قاعدة البيانات
                return reportService.exportReportToPdf(
                        "StudentIdCards.jrxml",
                        new HashMap<>(), // لا توجد بارامترات إضافية نحتاجها هنا
                        studentsList,
                        fileName
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(outputPath -> Platform.runLater(() -> {
            AlertUtils.showSuccess("نجاح العملية", "تم تصدير الكارنيهات بنجاح إلى:\n" + outputPath);

            // (اختياري) فتح الملف تلقائياً بعد إنشائه
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(outputPath));
            } catch (Exception e) {
                // تجاهل الخطأ إذا كان نظام التشغيل لا يدعم الفتح التلقائي
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                ex.printStackTrace();
                AlertUtils.showError("خطأ في التصدير", "فشلت عملية إنشاء الكارنيهات:\n" + ex.getCause().getMessage());
            });
            return null;
        });
    }
}