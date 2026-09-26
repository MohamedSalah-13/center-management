package com.codejava.center.controller;
import com.codejava.center.domain.Subject;
import com.codejava.center.service.SubjectService;
import com.codejava.center.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
@Controller @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) @RequiredArgsConstructor
public class SubjectsController {
    private final SubjectService service;
    @FXML private TableView<Subject> subjectTable;
    @FXML private TableColumn<Subject, String> nameColumn;
    @FXML private TextField nameField;
    @FXML private Button saveButton, deleteButton;
    @FXML public void initialize() {
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        nameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getName()));
        subjectTable.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> {
            nameField.setText(b == null ? "" : b.getName()); deleteButton.setDisable(b == null);
        });
        load();
    }
    private void load() { FxAsync.supply(service::getAllSubjects, rows -> subjectTable.getItems().setAll(rows), e -> Dialogs.error(FxAsync.messageOf(e))); }
    @FXML private void clear() { subjectTable.getSelectionModel().clearSelection(); nameField.clear(); }
    @FXML private void save() {
        Subject selected = subjectTable.getSelectionModel().getSelectedItem();
        Long id = selected == null ? null : selected.getId(); String name = nameField.getText();
        busy(true);
        FxAsync.supply(() -> service.save(id, name), saved -> { busy(false); clear(); load(); }, e -> { busy(false); Dialogs.error(FxAsync.messageOf(e)); });
    }
    @FXML private void delete() {
        Subject selected = subjectTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!Dialogs.confirm(I18n.format("subject.confirmDelete", selected.getName()))) return;
        Long id = selected.getId(); busy(true);
        FxAsync.run(() -> service.delete(id), () -> { busy(false); clear(); load(); }, e -> { busy(false); Dialogs.error(FxAsync.messageOf(e)); });
    }
    private void busy(boolean value) { saveButton.setDisable(value); deleteButton.setDisable(value || subjectTable.getSelectionModel().getSelectedItem() == null); nameField.setDisable(value); subjectTable.setDisable(value); }
}
