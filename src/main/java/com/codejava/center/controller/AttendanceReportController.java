package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.util.FxAsync;
import com.codejava.commons.fx.dialog.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;

/**
 * تقرير حضور وغياب مجموعة خلال فترة.
 * الغياب لا يُسجَّل في النظام بل يُستنتج، فعدد الغياب = الحصص المنعقدة ناقص ما حضره الطالب.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class AttendanceReportController {

    private final AttendanceService attendanceService;
    private final CourseGroupService courseGroupService;
    private final ReportService reportService;

    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private Label totalSessionsLabel;
    @FXML private CheckBox absenteesOnlyCheck;
    @FXML private Button printButton;

    @FXML private TableView<AttendanceSummary> reportTable;
    @FXML private TableColumn<AttendanceSummary, String> colName, colBarcode, colParentPhone,
            colAttended, colAbsent, colRate;

    private final ObservableList<AttendanceSummary> rows = FXCollections.observableArrayList();
    private long totalSessions = 0;
    private GroupAttendanceReport currentReport;

    @FXML
    public void initialize() {
        groupComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? "" : group.getName();
            }

            @Override
            public CourseGroup fromString(String string) {
                return null;
            }
        });

        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));
        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().barcode())));
        colParentPhone.setCellValueFactory(d -> new SimpleStringProperty(nullSafe(d.getValue().parentPhone())));
        colAttended.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().attended())));
        colAbsent.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(absencesOf(d.getValue()))));
        colRate.setCellValueFactory(d -> new SimpleStringProperty(rateOf(d.getValue())));

        FilteredList<AttendanceSummary> filtered = new FilteredList<>(rows, r -> true);
        absenteesOnlyCheck.selectedProperty().addListener((obs, oldVal, showAbsenteesOnly) ->
                filtered.setPredicate(r -> !showAbsenteesOnly || absencesOf(r) > 0));
        reportTable.setItems(filtered);

        fromPicker.setValue(LocalDate.now().minusMonths(1));
        toPicker.setValue(LocalDate.now());

        loadGroups();
    }

    private String nullSafe(String value) {
        return value == null ? "---" : value;
    }

    private long absencesOf(AttendanceSummary row) {
        return Math.max(0, totalSessions - row.attended());
    }

    private String rateOf(AttendanceSummary row) {
        if (totalSessions == 0) return "---";
        return String.format("%.0f%%", (row.attended() * 100.0) / totalSessions);
    }

    private void loadGroups() {
        FxAsync.supply(courseGroupService::getAllGroups,
                groups -> groupComboBox.getItems().setAll(groups),
                error -> AlertUtils.showError("خطأ", "تعذر تحميل المجموعات: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handleGenerate(ActionEvent event) {
        CourseGroup group = groupComboBox.getValue();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();

        if (group == null || from == null || to == null) {
            AlertUtils.showWarning("بيانات ناقصة", "يرجى اختيار المجموعة وتحديد الفترة.");
            return;
        }
        if (from.isAfter(to)) {
            AlertUtils.showWarning("فترة غير صحيحة", "تاريخ البداية يجب أن يسبق تاريخ النهاية.");
            return;
        }

        FxAsync.supply(() -> attendanceService.getGroupAttendance(group, from, to), report -> {
            currentReport = report;
            totalSessions = report.totalSessions();
            totalSessionsLabel.setText(String.valueOf(totalSessions));
            rows.setAll(report.rows());
            reportTable.refresh(); // أعمدة الغياب والنسبة تعتمد على totalSessions المحدَّث للتو
            printButton.setDisable(report.rows().isEmpty());

            if (totalSessions == 0) {
                AlertUtils.showWarning("تنبيه", "لم تُسجَّل أي حصة لهذه المجموعة خلال الفترة المحددة.");
            }
        }, error -> AlertUtils.showError("خطأ", "تعذر إنشاء التقرير: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handlePrint(ActionEvent event) {
        if (currentReport == null || currentReport.rows().isEmpty()) {
            AlertUtils.showWarning("تنبيه", "لا يوجد تقرير لطباعته.");
            return;
        }

        try {
            reportService.printAttendanceReport(currentReport,
                    fromPicker.getValue(), toPicker.getValue(),
                    ((Node) event.getSource()).getScene().getWindow());
        } catch (Exception e) {
            AlertUtils.showError("خطأ في الطباعة", FxAsync.messageOf(e));
        }
    }
}
