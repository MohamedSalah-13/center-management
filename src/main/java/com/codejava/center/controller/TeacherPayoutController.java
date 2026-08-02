package com.codejava.center.controller;

import com.codejava.center.service.TeacherService;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.MoneyUtils;
import com.codejava.commons.fx.dialog.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;

/**
 * صرف مستحقات المعلمين عن الحصص المغلقة.
 * كان منطق الحساب والصرف موجوداً في TeacherService منذ البداية بلا أي شاشة تستدعيه.
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class TeacherPayoutController {

    private final TeacherService teacherService;

    @FXML private TableView<SessionPayout> payoutsTable;
    @FXML private TableColumn<SessionPayout, String> colDate, colGroup, colTeacher,
            colAttendees, colRevenue, colType, colPayout;
    @FXML private Label totalPendingLabel;
    @FXML private Button payButton;

    private final ObservableList<SessionPayout> payouts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sessionDate().toString()));
        colGroup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().groupName()));
        colTeacher.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().teacherName()));
        colAttendees.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().attendees())));
        colRevenue.setCellValueFactory(d -> new SimpleStringProperty(MoneyUtils.format(d.getValue().totalRevenue())));
        colType.setCellValueFactory(d -> new SimpleStringProperty(commissionLabel(d.getValue().commissionType())));
        colPayout.setCellValueFactory(d -> new SimpleStringProperty(MoneyUtils.format(d.getValue().payoutAmount())));

        payoutsTable.setItems(payouts);
        payoutsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> payButton.setDisable(newVal == null));

        loadPayouts();
    }

    private String commissionLabel(String type) {
        return switch (type) {
            case "PERCENTAGE" -> "نسبة مئوية";
            case "FIXED_AMOUNT" -> "مبلغ ثابت";
            case "RENT" -> "إيجار قاعة";
            default -> type;
        };
    }

    private void loadPayouts() {
        FxAsync.supply(teacherService::getPayableSessions, list -> {
            payouts.setAll(list);
            BigDecimal total = list.stream()
                    .map(SessionPayout::payoutAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPendingLabel.setText(MoneyUtils.formatWithCurrency(total));
            payButton.setDisable(true);
        }, error -> AlertUtils.showError("خطأ", "تعذر تحميل المستحقات: " + FxAsync.messageOf(error)));
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPayouts();
    }

    @FXML
    public void handlePayout(ActionEvent event) {
        SessionPayout selected = payoutsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtils.showWarning("تنبيه", "يرجى تحديد الحصة المراد صرف مستحقاتها.");
            return;
        }

        // الصرف لا يتكرر، فالتأكيد يعرض المبلغ والمعلم صراحةً قبل التنفيذ
        boolean confirmed = AlertUtils.showConfirm("تأكيد الصرف", String.format(
                "صرف %s للمعلم %s%nعن حصة %s بتاريخ %s (حضور %d طالب)؟%n%nلا يمكن التراجع عن هذه العملية.",
                MoneyUtils.formatWithCurrency(selected.payoutAmount()),
                selected.teacherName(),
                selected.groupName(),
                selected.sessionDate(),
                selected.attendees()));

        if (!confirmed) return;

        FxAsync.run(() -> teacherService.processSessionPayout(selected.sessionId()), () -> {
            AlertUtils.showSuccess("نجاح", "تم صرف المستحقات وتسجيلها في الخزينة.");
            loadPayouts();
        }, error -> AlertUtils.showError("خطأ", FxAsync.messageOf(error)));
    }
}
