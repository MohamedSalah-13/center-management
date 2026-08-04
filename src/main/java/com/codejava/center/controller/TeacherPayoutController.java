package com.codejava.center.controller;

import com.codejava.center.service.TeacherService;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
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
        colType.setCellValueFactory(d -> new SimpleStringProperty(
                CommissionTypes.displayName(d.getValue().commissionType())));
        colPayout.setCellValueFactory(d -> new SimpleStringProperty(MoneyUtils.format(d.getValue().payoutAmount())));

        payoutsTable.setItems(payouts);
        payoutsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> payButton.setDisable(newVal == null));

        loadPayouts();
    }

    private void loadPayouts() {
        FxAsync.supply(teacherService::getPayableSessions, list -> {
            payouts.setAll(list);
            BigDecimal total = list.stream()
                    .map(SessionPayout::payoutAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPendingLabel.setText(MoneyUtils.formatWithCurrency(total));
            payButton.setDisable(true);
        }, error -> Dialogs.error(I18n.format("payout.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPayouts();
    }

    @FXML
    public void handlePayout(ActionEvent event) {
        SessionPayout selected = payoutsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.warning(I18n.get("payout.selectSession"));
            return;
        }

        // الصرف لا يتكرر، فالتأكيد يعرض المبلغ والمعلم صراحةً قبل التنفيذ
        boolean confirmed = Dialogs.confirm(I18n.get("payout.confirmTitle"), I18n.format("payout.confirm",
                MoneyUtils.formatWithCurrency(selected.payoutAmount()),
                selected.teacherName(),
                selected.groupName(),
                selected.sessionDate(),
                selected.attendees()));

        if (!confirmed) return;

        FxAsync.run(() -> teacherService.processSessionPayout(selected.sessionId()), () -> {
            Dialogs.success(I18n.get("payout.done"));
            loadPayouts();
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }
}
