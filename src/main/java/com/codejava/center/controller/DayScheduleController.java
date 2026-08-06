package com.codejava.center.controller;

import com.codejava.center.service.DayScheduleService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.dto.DayScheduleRow;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Sheets;
import com.codejava.center.util.WeekDays;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * جدول حصص اليوم: ما موعده اليوم، وما فُتح منه فعلاً.
 *
 * <p>شاشة الموظف على المكتب لا شاشة إدارة: هي تجيب "ماذا يعمل الآن، وماذا لم يبدأ بعد"،
 * بينما شاشة إدارة الحصص تفتح وتغلق. ولذلك لا قيد دور عليها - من يجلس أمام الباب هو من
 * يحتاجها أكثر من غيره.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class DayScheduleController {

    /** الصف الجاري الآن يُظلَّل بصنف style.css نفسه الذي تستعمله شاشة الحصص */
    private static final String OPEN_ROW_STYLE_CLASS = "session-open";

    private final DayScheduleService dayScheduleService;
    private final ReportService reportService;

    @FXML private DatePicker datePicker;
    @FXML private Label dayNameLabel;
    @FXML private Label summaryLabel;

    @FXML private TableView<DayScheduleRow> scheduleTable;
    @FXML private TableColumn<DayScheduleRow, String> colGroup;
    @FXML private TableColumn<DayScheduleRow, String> colTeacher;
    @FXML private TableColumn<DayScheduleRow, String> colLevel;
    @FXML private TableColumn<DayScheduleRow, String> colTime;
    @FXML private TableColumn<DayScheduleRow, String> colStatus;
    @FXML private TableColumn<DayScheduleRow, String> colStarted;
    @FXML private TableColumn<DayScheduleRow, String> colEnded;
    @FXML private TableColumn<DayScheduleRow, String> colAttendance;

    private final ObservableList<DayScheduleRow> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();

        datePicker.setValue(LocalDate.now());
        datePicker.setOnAction(event -> load());

        load();
    }

    private void setupTable() {
        bind(colGroup, DayScheduleRow::getGroupName);
        bind(colTeacher, DayScheduleRow::getTeacherName);
        bind(colLevel, DayScheduleRow::getLevel);
        bind(colTime, DayScheduleRow::getScheduledTime);
        bind(colStatus, DayScheduleRow::getStatus);
        bind(colStarted, DayScheduleRow::getStartedAt);
        bind(colEnded, DayScheduleRow::getEndedAt);
        bind(colAttendance, DayScheduleRow::getAttendance);

        // الحصة الجارية الآن هي ما يبحث عنه الموظف في الجدول، فتُظلَّل بدل أن تُقرأ
        // حالتها في عمود بين ثمانية أعمدة
        scheduleTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(DayScheduleRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove(OPEN_ROW_STYLE_CLASS);
                if (!empty && item != null && item.isOpen()) {
                    getStyleClass().add(OPEN_ROW_STYLE_CLASS);
                }
            }
        });

        scheduleTable.setItems(rows);
    }

    private void bind(TableColumn<DayScheduleRow, String> column, Function<DayScheduleRow, String> value) {
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
    }

    private void load() {
        LocalDate date = date();
        dayNameLabel.setText(WeekDays.displayName(date.getDayOfWeek()));

        FxAsync.supply(() -> dayScheduleService.getSchedule(date),
                schedule -> {
                    rows.setAll(schedule);
                    summaryLabel.setText(summarize(schedule));
                },
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    /** تاريخ فارغ يعني اليوم: مسح الخانة يجب ألا يترك الشاشة بلا جدول */
    private LocalDate date() {
        return datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
    }

    /**
     * الأرقام الأربعة التي تُقرأ من بعيد: كم مجموعة اليوم، وكم منها يعمل الآن،
     * وكم لم يُفتح بعد، وكم انتهى.
     */
    private String summarize(List<DayScheduleRow> schedule) {
        return I18n.format("daySchedule.summary", schedule.size(),
                count(schedule, DayScheduleRow.State.OPEN),
                count(schedule, DayScheduleRow.State.NOT_OPENED),
                count(schedule, DayScheduleRow.State.CLOSED));
    }

    private long count(List<DayScheduleRow> schedule, DayScheduleRow.State state) {
        return schedule.stream().filter(row -> row.getState() == state).count();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        load();
    }

    @FXML
    public void handleToday(ActionEvent event) {
        if (LocalDate.now().equals(datePicker.getValue())) {
            load(); // نفس اليوم: لا حدث تغيير يُطلق التحميل، والمقصود بالزر هو التحديث
        } else {
            datePicker.setValue(LocalDate.now()); // المستمع يعيد التحميل
        }
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        // نسخة من الصفوف والملخّص تُؤخذ هنا: البناء يجري في الخلفية، وقراءة قائمة
        // الجدول الحيّة من هناك تتعارض مع تعديلها من خيط الواجهة
        List<DayScheduleRow> shown = new ArrayList<>(rows);
        LocalDate date = date();
        String summary = summaryLabel.getText();

        FxAsync.supply(() -> reportService.deliverDaySchedule(date, shown, summary),
                Sheets::show,
                error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
    }
}
