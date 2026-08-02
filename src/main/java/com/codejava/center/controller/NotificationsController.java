package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.NotificationType;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.util.FxAsync;
import com.codejava.commons.fx.dialog.AlertUtils;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * إشعار أولياء الأمور بالغياب أو المتأخرات.
 * تعرض النص كاملاً قبل الإرسال، وتميّز الأرقام غير الصالحة ومن أُرسل له مسبقاً.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationService notificationService;
    private final AttendanceService attendanceService;
    private final StudentService studentService;
    private final CourseGroupService courseGroupService;

    @FXML private Label channelNoteLabel, summaryLabel, groupLabel, fromLabel, toLabel;
    @FXML private ComboBox<NotificationType> typeComboBox;
    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private CheckBox sendableOnlyCheck;
    @FXML private Button sendSelectedButton, sendAllButton;

    @FXML private TableView<NotificationCandidate> candidatesTable;
    @FXML private TableColumn<NotificationCandidate, String> colName, colPhone, colStatus, colMessage;

    private final ObservableList<NotificationCandidate> candidates = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        typeComboBox.getItems().addAll(NotificationType.values());
        typeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(NotificationType type) {
                return type == null ? "" : type.getArabicName();
            }

            @Override
            public NotificationType fromString(String string) {
                return null;
            }
        });
        typeComboBox.setValue(NotificationType.ABSENCE);
        typeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateFieldVisibility());

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
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().rawPhone().isBlank() ? "لا يوجد" : d.getValue().rawPhone()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().statusLabel()));
        colMessage.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().message()));

        candidatesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        FilteredList<NotificationCandidate> filtered = new FilteredList<>(candidates, c -> true);
        sendableOnlyCheck.selectedProperty().addListener((obs, oldVal, onlySendable) ->
                filtered.setPredicate(c -> !onlySendable || c.sendable()));
        filtered.setPredicate(NotificationCandidate::sendable);
        candidatesTable.setItems(filtered);

        fromPicker.setValue(LocalDate.now().minusWeeks(1));
        toPicker.setValue(LocalDate.now());

        channelNoteLabel.setText(notificationService.channelRequiresManualConfirmation()
                ? "القناة الحالية تفتح محادثة واتساب لكل ولي أمر بالنص جاهزاً، وتضغط أنت \"إرسال\". لا تُرسَل أي رسالة دون رؤيتك لها."
                : "القناة الحالية ترسل الرسائل مباشرةً دون تدخّل.");

        updateFieldVisibility();
        loadGroups();
    }

    /** حقول المجموعة والفترة تخص إشعار الغياب فقط؛ المتأخرات تشمل كل الطلاب */
    private void updateFieldVisibility() {
        boolean isAbsence = typeComboBox.getValue() == NotificationType.ABSENCE;
        for (javafx.scene.Node node : new javafx.scene.Node[]{
                groupLabel, groupComboBox, fromLabel, fromPicker, toLabel, toPicker}) {
            node.setVisible(isAbsence);
            node.setManaged(isAbsence);
        }
    }

    private void loadGroups() {
        FxAsync.supply(courseGroupService::getAllGroups,
                groups -> groupComboBox.getItems().setAll(groups),
                error -> AlertUtils.showError("خطأ", "تعذر تحميل المجموعات: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handleBuild(ActionEvent event) {
        NotificationType type = typeComboBox.getValue();

        if (type == NotificationType.ABSENCE) {
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

            FxAsync.supply(() -> notificationService.buildAbsenceNotifications(
                    attendanceService.getGroupAttendance(group, from, to)), this::showCandidates,
                    error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
        } else {
            FxAsync.supply(() -> notificationService.buildArrearsNotifications(
                    studentService.getStudentsInArrears()), this::showCandidates,
                    error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
        }
    }

    private void showCandidates(List<NotificationCandidate> list) {
        candidates.setAll(list);

        long ready = list.stream().filter(NotificationCandidate::sendable).count();
        long invalid = list.stream().filter(c -> !c.phoneValid()).count();
        long already = list.stream().filter(c -> c.phoneValid() && c.alreadyNotified()).count();

        summaryLabel.setText(String.format("الإجمالي: %d   |   جاهز: %d   |   رقم غير صالح: %d   |   أُرسل مسبقاً: %d",
                list.size(), ready, invalid, already));

        sendSelectedButton.setDisable(ready == 0);
        sendAllButton.setDisable(ready == 0);

        if (list.isEmpty()) {
            AlertUtils.showWarning("لا توجد نتائج", "لا يوجد من يستحق هذا الإشعار حالياً.");
        }
    }

    @FXML
    public void handleSendSelected(ActionEvent event) {
        List<NotificationCandidate> selected = new ArrayList<>(
                candidatesTable.getSelectionModel().getSelectedItems());

        if (selected.isEmpty()) {
            AlertUtils.showWarning("تنبيه", "يرجى تحديد صف واحد على الأقل.");
            return;
        }
        sendAll(selected.stream().filter(NotificationCandidate::sendable).toList());
    }

    @FXML
    public void handleSendAll(ActionEvent event) {
        sendAll(candidates.stream().filter(NotificationCandidate::sendable).toList());
    }

    private void sendAll(List<NotificationCandidate> targets) {
        if (targets.isEmpty()) {
            AlertUtils.showWarning("تنبيه", "لا يوجد مرسَل إليه جاهز ضمن ما حددته.");
            return;
        }

        // الإرسال لأشخاص حقيقيين: تأكيد صريح بالعدد قبل التنفيذ.
        // ومع القناة اليدوية سيُفتح تبويب لكل ولي أمر، وهو ما يجب أن يعرفه المستخدم مسبقاً.
        String warning = notificationService.channelRequiresManualConfirmation()
                ? String.format("%n%nستُفتح %d محادثة واتساب واحدة تلو الأخرى، وتضغط \"إرسال\" في كل منها.", targets.size())
                : "";

        if (!AlertUtils.showConfirm("تأكيد الإرسال",
                String.format("إرسال %s إلى %d ولي أمر؟%s",
                        typeComboBox.getValue().getArabicName(), targets.size(), warning))) {
            return;
        }

        FxAsync.supply(() -> {
            int sent = 0;
            List<String> failures = new ArrayList<>();
            for (NotificationCandidate candidate : targets) {
                MessageSender.SendResult result = notificationService.send(candidate);
                if (result.success()) {
                    sent++;
                } else {
                    failures.add(candidate.studentName() + ": " + result.failureReason());
                }
            }
            return new SendOutcome(sent, failures);
        }, outcome -> {
            if (outcome.failures().isEmpty()) {
                AlertUtils.showSuccess("تم", "تم إرسال " + outcome.sent() + " إشعاراً.");
            } else {
                AlertUtils.showError("اكتمل مع أخطاء", String.format(
                        "نجح: %d   -   فشل: %d%n%n%s",
                        outcome.sent(), outcome.failures().size(),
                        String.join("\n", outcome.failures())));
            }
            handleBuild(null); // إعادة التجهيز لتحديث حالة "أُرسل مسبقاً"
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }

    private record SendOutcome(int sent, List<String> failures) {
    }
}
