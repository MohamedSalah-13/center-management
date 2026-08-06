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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class SessionManagementController {

    /** الصف المفتوح يُظلَّل بهذا الصنف؛ لونه في style.css لا هنا */
    private static final String OPEN_ROW_STYLE_CLASS = "session-open";

    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SessionService sessionService;
    private final CourseGroupService courseGroupService;

    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private DatePicker sessionDatePicker;

    @FXML private DatePicker filterDatePicker;
    @FXML private ComboBox<Boolean> statusFilterCombo;
    @FXML private Label countLabel;

    @FXML private TableView<Session> sessionsTable;
    @FXML private TableColumn<Session, String> colId;
    @FXML private TableColumn<Session, String> colGroup;
    @FXML private TableColumn<Session, String> colDate;
    @FXML private TableColumn<Session, String> colStart;
    @FXML private TableColumn<Session, String> colEnd;
    @FXML private TableColumn<Session, String> colStatus;

    private final ObservableList<Session> sessionsList = FXCollections.observableArrayList();

    /** حارس إعادة الدخول: ضبط قيم التصفية برمجياً يُطلق حدثها، فيُحمَّل الجدول مرتين بلا سبب */
    private boolean adjustingFilters;

    @FXML
    public void initialize() {
        setupTable();
        setupComboBox();
        setupFilters();
        loadData();
    }

    private void setupTable() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colGroup.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGroup().getName()));
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSessionDate().toString()));

        colStart.setCellValueFactory(data -> new SimpleStringProperty(
                moment(data.getValue().getStartedAt(), data.getValue().getSessionDate())));
        colEnd.setCellValueFactory(data -> new SimpleStringProperty(
                moment(data.getValue().getEndedAt(), data.getValue().getSessionDate())));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                I18n.get(data.getValue().isActive() ? "session.status.active" : "session.status.closed")));

        // التظليل بصنف لا بـ setStyle مباشر: النمط المكتوب على الصف يغلب تنسيق التحديد،
        // فيبقى الصف المفتوح بلونه حين يُختار ولا يظهر أنه محدَّد أصلاً
        sessionsTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(Session item, boolean empty) {
                super.updateItem(item, empty);
                boolean open = !empty && item != null && item.isActive();
                getStyleClass().remove(OPEN_ROW_STYLE_CLASS);
                if (open) {
                    getStyleClass().add(OPEN_ROW_STYLE_CLASS);
                }
            }
        });

        sessionsTable.setItems(sessionsList);
    }

    /**
     * الساعة وحدها ما دامت اللحظة في يوم الحصة نفسه، ومعها التاريخ إن خرجت عنه.
     *
     * <p>الحصة تُترك مفتوحة إلى صباح اليوم التالي أحياناً، و"09:15" وحدها في صفٍّ
     * تاريخه أمس تُقرأ على أنها انتهت قبل أن تبدأ. والتاريخ لا يُكتب في كل صف لأنه
     * حينها تكرارٌ لعمود التاريخ المجاور في كل حصة عادية.</p>
     *
     * <p>الحصص المسجَّلة قبل هذه الإضافة بلا وقت، والحصة المفتوحة بلا نهاية بعد:
     * خانة فارغة تقول "غير معلوم" ولا تخترع رقماً.</p>
     */
    private String moment(LocalDateTime instant, LocalDate sessionDate) {
        if (instant == null) {
            return I18n.get("common.empty");
        }
        return instant.toLocalDate().equals(sessionDate)
                ? instant.format(TIME_ONLY) : instant.format(DATE_AND_TIME);
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

    private void setupFilters() {
        // العنصر null معناه "الكل"، ووجوده داخل القائمة هو ما يتيح العودة إليه بعد اختيار حالة
        statusFilterCombo.getItems().setAll(null, Boolean.TRUE, Boolean.FALSE);
        statusFilterCombo.setCellFactory(list -> statusCell());
        statusFilterCombo.setButtonCell(statusCell());
        statusFilterCombo.setValue(null);

        // اليوم هو المبدأ: الحصص المقصودة في شاشة الإدارة هي حصص اليوم،
        // ومن أراد ما قبله ضغط "عرض الكل" أو اختار تاريخاً
        filterDatePicker.setValue(LocalDate.now());

        filterDatePicker.setOnAction(event -> onFilterChanged());
        statusFilterCombo.setOnAction(event -> onFilterChanged());
    }

    /**
     * خلية مكتوبة لا {@code StringConverter}: الخلية الافتراضية لا تسأل المحوّل حين تكون
     * القيمة {@code null}، فكان خيار "الكل" - وهو الوضع الابتدائي - يظهر فراغاً.
     */
    private ListCell<Boolean> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : I18n.get(item == null ? "common.all"
                        : item ? "session.status.active" : "session.status.closed"));
            }
        };
    }

    private void onFilterChanged() {
        if (!adjustingFilters) {
            loadSessions();
        }
    }

    /** تحميل المجموعات والحصص معاً - عند فتح الشاشة وعند طلب التحديث */
    private void loadData() {
        FxAsync.supply(() -> new LoadedData(courseGroupService.getAllGroups(), readSessions()),
                data -> {
                    keepSelectedGroup(data.groups());
                    showSessions(data.sessions());
                },
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    /** تحميل الجدول وحده: كل تغيير في التصفية رحلة إلى القاعدة، والمجموعات لم تتغير */
    private void loadSessions() {
        FxAsync.supply(this::readSessions, this::showSessions,
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    private List<Session> readSessions() {
        return sessionService.findSessions(filterDatePicker.getValue(), statusFilterCombo.getValue());
    }

    private void showSessions(List<Session> sessions) {
        sessionsList.setAll(sessions);
        countLabel.setText(I18n.format("session.count", sessions.size()));
    }

    /**
     * إعادة تحميل قائمة المجموعات تُبدّل الكائنات، فيفقد الاختيار ما لم يُستعَد
     * من القائمة الجديدة - والموظف الذي ضغط "تحديث" لم يطلب إلغاء اختياره.
     */
    private void keepSelectedGroup(List<CourseGroup> groups) {
        CourseGroup previous = groupComboBox.getValue();
        groupComboBox.getItems().setAll(groups);

        if (previous != null) {
            groups.stream()
                    .filter(group -> Objects.equals(group.getId(), previous.getId()))
                    .findFirst()
                    .ifPresent(groupComboBox::setValue);
        }
    }

    private record LoadedData(List<CourseGroup> groups, List<Session> sessions) {
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadData();
    }

    @FXML
    public void handleShowAll(ActionEvent event) {
        setFilters(null, null);
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
                    // الحصة الجديدة قد تقع خارج التصفية القائمة فتُفتح دون أن تُرى؛
                    // تُنقل التصفية إلى يومها بدل تركها تختفي لحظة فتحها
                    setFilters(newSession.getSessionDate(), null);
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
            loadSessions(); // إعادة تحميل البيانات لتحديث حالة الجدول
            Dialogs.success(I18n.format("session.closed", selected.getGroup().getName()));
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    /** ضبط التصفية ثم تحميل واحد: الحارس يمنع تحميلاً لكل حقل يتغيّر */
    private void setFilters(LocalDate date, Boolean active) {
        adjustingFilters = true;
        try {
            filterDatePicker.setValue(date);
            statusFilterCombo.setValue(active);
        } finally {
            adjustingFilters = false;
        }
        loadSessions();
    }
}
