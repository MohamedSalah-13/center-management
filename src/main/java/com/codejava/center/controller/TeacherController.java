package com.codejava.center.controller;

import com.codejava.center.domain.Teacher;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TeacherService;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import com.codejava.commons.fx.form.FormUtils;
import com.codejava.commons.fx.validation.InputValidator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class TeacherController {

    private final ReportService reportService;
    private final TeacherService teacherService;
    private final ObservableList<Teacher> teachersList = FXCollections.observableArrayList();
    @FXML
    private TextField nameField, subjectField, valueField;
    @FXML private TextField searchField;
    @FXML
    private ComboBox<String> typeCombo;
    @FXML
    private TableView<Teacher> teacherTable;
    @FXML
    private TableColumn<Teacher, String> colName, colSubject, colType, colValue;
    @FXML private Button updateButton, deleteButton, printButton;
    private Teacher selectedTeacher = null;

    @FXML
    public void initialize() {
        typeCombo.getItems().addAll("PERCENTAGE", "FIXED_AMOUNT", "RENT");

        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        colSubject.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSubject()));
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCommissionType()));
        colValue.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(MoneyUtils.format(d.getValue().getCommissionValue())));

        teacherTable.setItems(teachersList);
        setupTableSelectionListener();
        loadTeachers();

        // تأمين خانة المبلغ
        InputValidator.makeDecimalOnly(valueField);
        FormUtils.focusNextOnEnter(nameField, subjectField);


        FilteredList<Teacher> filteredData = new FilteredList<>(teachersList, b -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(teacher -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                if (teacher.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (teacher.getSubject().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        SortedList<Teacher> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(teacherTable.comparatorProperty());
        teacherTable.setItems(sortedData);
    }

    private void setupTableSelectionListener() {
        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTeacher = newVal;
                nameField.setText(selectedTeacher.getName());
                subjectField.setText(selectedTeacher.getSubject());
                typeCombo.setValue(selectedTeacher.getCommissionType());
                valueField.setText(MoneyUtils.format(selectedTeacher.getCommissionValue()));

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
                printButton.setDisable(false); // تفعيل زر الطباعة
            }
        });
    }

    @FXML
    public void handlePrintAction(javafx.event.ActionEvent event) {
        if (selectedTeacher == null) return;

        // جلب النافذة الحالية لربط حوار الطباعة بها
        javafx.stage.Window window = ((Node) event.getSource()).getScene().getWindow();

        Teacher target = selectedTeacher;

        // جلب الحصص في الخلفية، ثم الطباعة على خيط الواجهة (شرط PrinterJob في JavaFX)
        FxAsync.supply(() -> teacherService.getPayableSessionsOf(target.getId()),
                sessions -> {
                    try {
                        reportService.printTeacherStatement(target, sessions, window);
                    } catch (Exception e) {
                        AlertUtils.showError("خطأ", "حدث خطأ أثناء الطباعة: " + FxAsync.messageOf(e));
                    }
                },
                error -> AlertUtils.showError("خطأ", "تعذر تحميل حصص المعلم: " + FxAsync.messageOf(error)));
    }

    @FXML
    private void clearFields() {
        nameField.clear();
        subjectField.clear();
        valueField.clear();
        typeCombo.setValue(null);

        selectedTeacher = null;
        teacherTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
        printButton.setDisable(true); // تعطيل زر الطباعة
    }

    private void loadTeachers() {
        FxAsync.supply(teacherService::getAllTeachers,
                teachers -> teachersList.setAll(teachers),
                error -> AlertUtils.showError("خطأ", "تعذر تحميل المعلمين: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handleSaveAction() {
        saveOrUpdateTeacher(new Teacher());
    }

    @FXML
    public void handleUpdateAction() {
        if (selectedTeacher != null) {
            saveOrUpdateTeacher(selectedTeacher);
        }
    }

    private void saveOrUpdateTeacher(Teacher teacher) {
        try {
            teacher.setName(nameField.getText());
            teacher.setSubject(subjectField.getText());
            teacher.setCommissionType(typeCombo.getValue());
            teacher.setCommissionValue(new BigDecimal(valueField.getText().trim()));
        } catch (NumberFormatException e) {
            AlertUtils.showError("إدخال خاطئ", "قيمة العمولة يجب أن تكون رقماً.");
            return;
        }

        boolean isNew = teacher.getId() == null;

        FxAsync.supply(() -> teacherService.saveTeacher(teacher), saved -> {
            if (isNew) {
                teachersList.add(saved); // إضافة جديد
            } else {
                // البحث عن الموضع في القائمة المصدر وليس في العرض (الجدول مربوط بـ SortedList/FilteredList
                // ولذلك يختلف ترتيب صفوفه عن teachersList عند الفرز أو البحث)
                int idx = teachersList.indexOf(teacher);
                if (idx >= 0) {
                    teachersList.set(idx, saved); // تحديث صف موجود
                }
            }
            clearFields();
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }

    @FXML
    public void handleDeleteAction() {
        if (selectedTeacher == null) return;

        if (AlertUtils.showConfirm("تأكيد الحذف", "حذف المعلم: " + selectedTeacher.getName() + "؟")) {
            Teacher target = selectedTeacher;
            FxAsync.run(() -> teacherService.deleteTeacher(target.getId()), () -> {
                teachersList.remove(target);
                clearFields();
            }, error -> AlertUtils.showError("خطأ", "لا يمكن الحذف لارتباط المعلم بمجموعات دراسية."));
        }
    }

}