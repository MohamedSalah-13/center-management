package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.SessionService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
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
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                I18n.get(data.getValue().isActive() ? "session.status.active" : "session.status.closed")));

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
        FxAsync.supply(() -> new LoadedData(courseGroupService.getAllGroups(), sessionService.getAllSessions()),
                data -> {
                    groupComboBox.getItems().setAll(data.groups());
                    sessionsList.setAll(data.sessions());
                },
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    private record LoadedData(java.util.List<CourseGroup> groups, java.util.List<Session> sessions) {
    }

    @FXML
    public void handleOpenSession(ActionEvent event) {
        CourseGroup selectedGroup = groupComboBox.getValue();
        if (selectedGroup == null) {
            Dialogs.warning(I18n.get("session.selectGroup"));
            return;
        }

        FxAsync.supply(() -> sessionService.openSession(selectedGroup, sessionDatePicker.getValue()),
                newSession -> {
                    sessionsList.add(newSession);
                    Dialogs.success(I18n.get("session.opened"));
                },
                error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    @FXML
    public void handleCloseSession(ActionEvent event) {
        // أكثر من حصة قد تكون مفتوحة في نفس الوقت (قاعات متوازية)،
        // لذلك يجب تحديد أي حصة تُغلق بدلاً من افتراض وجود حصة واحدة
        Session selected = sessionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.warning(I18n.get("session.selectToClose"));
            return;
        }

        FxAsync.run(() -> sessionService.closeSession(selected.getId()), () -> {
            loadData(); // إعادة تحميل البيانات لتحديث حالة الجدول
            Dialogs.success(I18n.format("session.closed", selected.getGroup().getName()));
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }
}