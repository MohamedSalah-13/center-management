package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceState;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.Durations;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Sheets;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * كشف الحضور والانصراف: من دخل ومن خرج ومتى، في أي يوم مضى.
 *
 * <p>غير {@code AttendanceReportController}: ذاك كشفٌ تجميعي يقول كم حصةً حضر الطالب
 * وكم غاب، وهذا سجلُّ أوقات - سطرٌ لكل مرة دخل فيها طالبٌ السنتر. والسؤالان مختلفان:
 * الأول لولي الأمر آخر الشهر، والثاني لمن يريد أن يعرف من كان في المبنى الساعة السابعة.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class AttendanceLogController {

    private final AttendanceService attendanceService;
    private final CourseGroupService courseGroupService;
    private final ReportService reportService;

    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private TextField searchField;
    @FXML private CheckBox notCheckedOutCheck;
    @FXML private Button printButton;
    @FXML private Label summaryLabel;

    @FXML private TableView<AttendanceLogRow> logTable;
    @FXML private TableColumn<AttendanceLogRow, String> colDate, colName, colBarcode, colGroup,
            colTimeIn, colTimeOut, colDuration, colState, colParentPhone;

    private final ObservableList<AttendanceLogRow> rows = FXCollections.observableArrayList();
    private final FilteredList<AttendanceLogRow> filtered = new FilteredList<>(rows, row -> true);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    @FXML
    public void initialize() {
        setupGroupComboBox();
        setupTable();

        // اليوم افتراضاً: أكثر ما يُسأل عنه هذا الكشف هو ما يجري الآن
        fromPicker.setValue(LocalDate.now());
        toPicker.setValue(LocalDate.now());

        searchField.textProperty().addListener((obs, was, is) -> applyFilters());
        notCheckedOutCheck.selectedProperty().addListener((obs, was, is) -> applyFilters());

        loadGroups();
        handleGenerate(null);
    }

    /** الصف الفارغ في أعلى القائمة يعني "كل المجموعات"، وهو الوضع الافتراضي */
    private void setupGroupComboBox() {
        groupComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? I18n.get("common.all") : group.getName();
            }

            @Override
            public CourseGroup fromString(String string) {
                return null;
            }
        });
    }

    private void setupTable() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().sessionDate())));
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));
        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().barcode())));
        colGroup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().groupName()));
        colTimeIn.setCellValueFactory(d -> new SimpleStringProperty(clock(d.getValue().timeIn())));
        colTimeOut.setCellValueFactory(d -> new SimpleStringProperty(clock(d.getValue().timeOut())));
        colDuration.setCellValueFactory(d -> new SimpleStringProperty(Durations.format(d.getValue().duration())));
        colState.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().state().getDisplayName()));
        colParentPhone.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().parentPhone())));

        logTable.setItems(filtered);
        logTable.setPlaceholder(new Label(I18n.get("common.noData")));
    }

    private void loadGroups() {
        FxAsync.supply(courseGroupService::getAllGroups, groups -> {
            List<CourseGroup> withAll = new ArrayList<>();
            withAll.add(null);
            withAll.addAll(groups);
            groupComboBox.getItems().setAll(withAll);
            groupComboBox.getSelectionModel().selectFirst();
        }, error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleGenerate(ActionEvent event) {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();

        if (from == null || to == null) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("attLog.selectPeriod"));
            return;
        }
        if (from.isAfter(to)) {
            Dialogs.warning(I18n.get("attReport.invalidPeriodTitle"), I18n.get("attReport.invalidPeriod"));
            return;
        }

        CourseGroup group = groupComboBox.getValue();
        Long groupId = group == null ? null : group.getId();

        FxAsync.supply(() -> attendanceService.getAttendanceLog(from, to, groupId), loaded -> {
            rows.setAll(loaded);
            applyFilters();
        }, error -> Dialogs.error(I18n.format("attLog.generateFailed", FxAsync.messageOf(error))));
    }

    /**
     * البحث وخانة "من لم ينصرف" في الذاكرة، والفترة والمجموعة في قاعدة البيانات.
     *
     * <p>الفصل مقصود: الفترة والمجموعة تحدّان حجم ما يُقرأ أصلاً، أما البحث فيُكتب حرفاً
     * حرفاً ولا يصحّ أن يرسل استعلاماً مع كل حرف.</p>
     */
    private void applyFilters() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean notCheckedOutOnly = notCheckedOutCheck.isSelected();

        filtered.setPredicate(row -> {
            if (notCheckedOutOnly && row.state() == AttendanceState.LEFT) return false;
            if (needle.isEmpty()) return true;

            return row.studentName().toLowerCase().contains(needle)
                    || (row.barcode() != null && row.barcode().toLowerCase().contains(needle));
        });

        updateSummary();
        printButton.setDisable(filtered.isEmpty());
    }

    /**
     * الإجماليات محسوبة على المعروض لا على المقروء: من يصفّي بمجموعة ثم يقرأ "عدد
     * الحضور 300" يقرأ رقم السنتر كله وينسبه إلى المجموعة.
     */
    private void updateSummary() {
        long total = filtered.size();
        long left = filtered.stream().filter(row -> row.state() == AttendanceState.LEFT).count();
        long inside = filtered.stream().filter(row -> row.state() == AttendanceState.INSIDE).count();

        summaryLabel.setText(I18n.format("attLog.summary", total, left, inside, Durations.format(averageStay())));
    }

    /** متوسط المكوث لمن سُجّل انصرافه وحدهم: من لم يُسجَّل لا مدة له تُضاف */
    private Duration averageStay() {
        List<Duration> stays = filtered.stream()
                .filter(row -> row.state() == AttendanceState.LEFT)
                .map(AttendanceLogRow::duration)
                .toList();

        if (stays.isEmpty()) return null;

        long totalSeconds = stays.stream().mapToLong(Duration::getSeconds).sum();
        return Duration.ofSeconds(totalSeconds / stays.size());
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        if (filtered.isEmpty()) {
            Dialogs.warning(I18n.get("attLog.nothingToPrint"));
            return;
        }

        List<AttendanceLogRow> printed = List.copyOf(filtered);
        String description = filterDescription();

        FxAsync.supply(() -> reportService.deliverAttendanceLog(printed, description),
                Sheets::show,
                error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
    }

    /** وصف التصفية يُطبع على الورقة: كشفٌ يُقرأ بعد شهر بلا وصفٍ يبدو كشف السنتر كله */
    private String filterDescription() {
        CourseGroup group = groupComboBox.getValue();
        return I18n.format("attLog.filterDescription",
                fromPicker.getValue(), toPicker.getValue(),
                group == null ? I18n.get("common.all") : group.getName());
    }

    private String clock(LocalDateTime moment) {
        return moment == null ? I18n.get("common.empty") : moment.format(timeFormatter);
    }

    private String nullSafe(String value) {
        return value == null ? I18n.get("common.none") : value;
    }
}
