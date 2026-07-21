package com.codejava.center.controller;

import com.codejava.center.domain.Student;
import com.codejava.center.service.StudentService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class StudentRegistrationController {

    private final StudentService studentService;

    @FXML private TextField nameField, phoneField, parentPhoneField, barcodeField;
    @FXML private ComboBox<String> schoolLevelCombo;

    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> colBarcode, colName, colPhone, colLevel;

    @FXML private Button updateButton, deleteButton;

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
        setupTableSelectionListener();
        loadStudents();
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
            }
        });
    }

    private void loadStudents() {
        CompletableFuture.supplyAsync(studentService::getAllStudents)
                .thenAccept(students -> Platform.runLater(() -> studentsList.setAll(students)));
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
        try {
            student.setName(nameField.getText());
            student.setPhone(phoneField.getText());
            student.setParentPhone(parentPhoneField.getText());
            student.setSchoolLevel(schoolLevelCombo.getValue());
            student.setBarcode(barcodeField.getText().isEmpty() ? null : barcodeField.getText());
            student.setActive(true);

            Student saved = studentService.saveStudent(student);

            if (student.getId() == null) {
                studentsList.add(saved);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم الحفظ. الباركود: " + saved.getBarcode());
            } else {
                int idx = studentTable.getSelectionModel().getSelectedIndex();
                studentsList.set(idx, saved);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم التعديل بنجاح.");
            }
            handleClearAction(null);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedStudent == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "حذف الطالب: " + selectedStudent.getName() + "؟", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    studentService.deleteStudent(selectedStudent.getId());
                    studentsList.remove(selectedStudent);
                    handleClearAction(null);
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "خطأ", "لا يمكن حذف الطالب بسبب وجود حركات مالية أو حضور مسجلة له.");
                }
            }
        });
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
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}