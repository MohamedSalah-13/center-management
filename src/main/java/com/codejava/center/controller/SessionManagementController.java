package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.SessionService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class SessionManagementController {

    private final SessionService sessionService;
    private final CourseGroupService courseGroupService;

    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private DatePicker sessionDatePicker;

    @FXML private TableView<Session> sessionsTable;
    @FXML private TableColumn<Session, String> colId;
    @FXML private TableColumn<Session, String> colGroup;
    @FXML private TableColumn<Session, String> colDate;
    @FXML private TableColumn<Session, String> colStatus;

    private final ObservableList<Session> sessionsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        setupComboBox();
        loadData();
    }

    private void setupTable() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colGroup.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGroup().getName()));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSessionDate().toString()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isActive() ? "نشطة" : "مغلقة"));

        sessionsTable.setItems(sessionsList);
    }

    private void setupComboBox() {
        groupComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? "" : group.getName();
            }
            @Override
            public CourseGroup fromString(String string) { return null; }
        });
    }

    private void loadData() {
        CompletableFuture.runAsync(() -> {
            var groups = courseGroupService.getAllGroups();
            var sessions = sessionService.getAllSessions();
            Platform.runLater(() -> {
                groupComboBox.getItems().setAll(groups);
                sessionsList.setAll(sessions);
            });
        });
    }

    @FXML
    public void handleOpenSession(ActionEvent event) {
        CourseGroup selectedGroup = groupComboBox.getValue();
        if (selectedGroup == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المجموعة الدراسية.");
            return;
        }

        try {
            Session newSession = sessionService.openSession(selectedGroup, sessionDatePicker.getValue());
            sessionsList.add(newSession);
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم فتح الحصة بنجاح.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    @FXML
    public void handleCloseSession(ActionEvent event) {
        try {
            sessionService.closeActiveSession();
            loadData(); // إعادة تحميل البيانات لتحديث حالة الجدول
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم إغلاق الحصة النشطة.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}