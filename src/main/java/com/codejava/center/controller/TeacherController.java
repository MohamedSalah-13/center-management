package com.codejava.center.controller;

import com.codejava.center.domain.Teacher;
import com.codejava.center.service.TeacherService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final ObservableList<Teacher> teachersList = FXCollections.observableArrayList();
    @FXML
    private TextField nameField, subjectField, valueField;
    @FXML
    private ComboBox<String> typeCombo;
    @FXML
    private TableView<Teacher> teacherTable;
    @FXML
    private TableColumn<Teacher, String> colName, colSubject, colType, colValue;
    @FXML
    private Button updateButton, deleteButton;
    private Teacher selectedTeacher = null;

    @FXML
    public void initialize() {
        typeCombo.getItems().addAll("PERCENTAGE", "FIXED_AMOUNT", "RENT");

        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        colSubject.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSubject()));
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCommissionType()));
        colValue.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getCommissionValue())));

        teacherTable.setItems(teachersList);
        setupTableSelectionListener();
        loadTeachers();
    }

    private void setupTableSelectionListener() {
        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTeacher = newVal;
                nameField.setText(selectedTeacher.getName());
                subjectField.setText(selectedTeacher.getSubject());
                typeCombo.setValue(selectedTeacher.getCommissionType());
                valueField.setText(String.valueOf(selectedTeacher.getCommissionValue()));

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    private void loadTeachers() {
        CompletableFuture.supplyAsync(teacherService::getAllTeachers)
                .thenAccept(teachers -> Platform.runLater(() -> teachersList.setAll(teachers)));
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
            teacher.setCommissionValue(Double.parseDouble(valueField.getText()));

            Teacher saved = teacherService.saveTeacher(teacher);

            if (teacher.getId() == null) {
                teachersList.add(saved); // إضافة جديد
            } else {
                int idx = teacherTable.getSelectionModel().getSelectedIndex();
                teachersList.set(idx, saved); // تحديث صف موجود
            }
            clearFields();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "خطأ: " + e.getMessage()).show();
        }
    }

    @FXML
    public void handleDeleteAction() {
        if (selectedTeacher == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "حذف المعلم: " + selectedTeacher.getName() + "؟", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    teacherService.deleteTeacher(selectedTeacher.getId());
                    teachersList.remove(selectedTeacher);
                    clearFields();
                } catch (Exception e) {
                    new Alert(Alert.AlertType.ERROR, "لا يمكن الحذف لارتباط المعلم بمجموعات دراسية.").show();
                }
            }
        });
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
    }
}