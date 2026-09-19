package com.codejava.center.controller;

import com.codejava.center.domain.Session;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceOutcome;
import com.codejava.center.service.dto.AttendanceResult;
import com.codejava.center.service.dto.AttendanceState;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.Durations;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * بوابة الحضور والانصراف.
 *
 * <p>الجدول كشفُ اليوم كما هو في قاعدة البيانات، لا سجلُّ ما جرى على هذه الشاشة منذ
 * فُتحت: المتحكّم {@code PROTOTYPE} - يُعاد بناؤه في كل تنقّل عمداً - فقائمةٌ في الذاكرة
 * تعني أن الرجوع إلى الشاشة يجدها فارغة، ولا أحد يعرف من بالداخل ومن غادر.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SessionService sessionService;

    @FXML private ComboBox<Session> sessionComboBox;
    @FXML private TextField barcodeScannerField;
    @FXML private VBox resultCard;
    @FXML private Label studentNameLabel;
    @FXML private Label groupNameLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<AttendanceLogRow> attendanceLogTable;
    @FXML private TableColumn<AttendanceLogRow, String> colName, colGroup, colTimeIn, colTimeOut,
            colDuration, colState;
    @FXML private TableColumn<AttendanceLogRow, Void> colAction;
    @FXML private CheckBox insideOnlyCheck;
    @FXML private Label insideCountLabel;

    @FXML private TableView<RejectedScan> rejectedTable;
    @FXML private TableColumn<RejectedScan, String> colRejectedTime, colRejectedName, colRejectedReason;

    private final ObservableList<AttendanceLogRow> dayRows = FXCollections.observableArrayList();
    private final ObservableList<RejectedScan> rejectedScans = FXCollections.observableArrayList();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
    private Timeline durationTicker;

    /** آخر ما رُدَّ من محاولات يبقى معروضاً؛ ما قبله لا يُسأل عنه أحد */
    private static final int MAX_REJECTED = 20;

    @FXML
    public void initialize() {
        setupSessionComboBox();
        setupDayTable();
        setupRejectedTable();
        startDurationTicker();

        // الاحتفاظ بالتركيز: إعادة التركيز لحقل الباركود دائماً
        Platform.runLater(() -> barcodeScannerField.requestFocus());
        barcodeScannerField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                Platform.runLater(() -> barcodeScannerField.requestFocus());
            }
        });

        resetResultCard();
        loadTodayLog();
    }

    /**
     * قائمة الحصص المفتوحة حالياً. تركها فارغة يعني الوضع التلقائي:
     * النظام يستنتج الحصة من مجموعات الطالب. تحديد حصة يعني أن هذا الجهاز
     * يخدم قاعة بعينها فيُسجَّل كل مسح عليها.
     */
    private void setupSessionComboBox() {
        sessionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Session session) {
                return session == null
                        ? I18n.get("attendance.autoSession")
                        : I18n.format("attendance.sessionLabel",
                                session.getGroup().getName(), session.getSessionDate());
            }

            @Override
            public Session fromString(String string) {
                return null;
            }
        });

        FxAsync.supply(sessionService::getActiveSessions,
                sessions -> sessionComboBox.getItems().setAll(sessions),
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    private void setupDayTable() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));
        colGroup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().groupName()));
        colTimeIn.setCellValueFactory(d -> new SimpleStringProperty(clock(d.getValue().timeIn())));
        colTimeOut.setCellValueFactory(d -> new SimpleStringProperty(clock(d.getValue().timeOut())));
        colDuration.setCellValueFactory(d -> new SimpleStringProperty(Durations.format(d.getValue().duration())));
        colState.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().state().getDisplayName()));
        setupCheckOutColumn();

        FilteredList<AttendanceLogRow> filtered = new FilteredList<>(dayRows, row -> true);
        insideOnlyCheck.selectedProperty().addListener((obs, was, insideOnly) ->
                filtered.setPredicate(row -> !insideOnly || row.state() == AttendanceState.INSIDE));
        attendanceLogTable.setItems(filtered);
    }

    /** الزرّ لمن نسي تمرير كارنيهه عند خروجه؛ ولا يظهر لمن سُجّل انصرافه بالفعل */
    private void setupCheckOutColumn() {
        colAction.setCellFactory(column -> new TableCell<>() {
            private final Button checkOutButton = new Button(I18n.get("attendance.checkOut"));

            {
                checkOutButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 2 10;");
                checkOutButton.setOnAction(event -> checkOut(getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                // فحص المدى قبل القراءة: الجدول مُصفّى وصفوفه تُبدَّل تحت الخلية،
                // فقد يُستدعى التحديث بترتيب لم يعد له صفّ
                boolean inside = !empty
                        && getIndex() < getTableView().getItems().size()
                        && getTableView().getItems().get(getIndex()).state() == AttendanceState.INSIDE;
                setGraphic(inside ? checkOutButton : null);
            }
        });
    }

    private void setupRejectedTable() {
        colRejectedTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTime()));
        colRejectedName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colRejectedReason.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReason()));
        rejectedTable.setItems(rejectedScans);
        rejectedTable.setPlaceholder(new Label(I18n.get("attendance.noRejected")));
    }

    /**
     * مدة مكوث من بالداخل تكبر ما دام في القاعة، فتُحسب من جديد كل دقيقة.
     *
     * <p>ويتوقف المؤقّت حين تفارق الشاشةُ المشهد: المتحكّم {@code PROTOTYPE} يُترك بلا
     * نافذة عند أول تنقّل، ومؤقّتٌ بداخله يظلّ يعمل إلى أن يُغلق البرنامج - وهي علّة
     * {@code AlertFeed} نفسها التي جعلت المؤقّت يسكن الخدمة لا المتحكّم.</p>
     */
    private void startDurationTicker() {
        durationTicker = new Timeline(new javafx.animation.KeyFrame(Duration.minutes(1),
                event -> attendanceLogTable.refresh()));
        durationTicker.setCycleCount(Timeline.INDEFINITE);
        durationTicker.play();

        attendanceLogTable.sceneProperty().addListener((obs, was, scene) -> {
            if (scene == null) {
                durationTicker.stop();
            }
        });
    }

    private void loadTodayLog() {
        FxAsync.supply(attendanceService::getTodayLog, rows -> {
            dayRows.setAll(rows);
            afterRowsChanged();
        }, error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        // جهازٌ آخر في السنتر قد يكون سجّل طلاباً لا يعرف عنهم هذا الجهاز شيئاً
        loadTodayLog();
    }

    @FXML
    public void handleBarcodeScan(ActionEvent event) {
        String barcode = barcodeScannerField.getText().trim();
        if (barcode.isEmpty()) return;

        // قراءة الحصة المحددة على خيط الواجهة قبل الانتقال للخلفية
        Session bound = sessionComboBox.getValue();
        Long boundSessionId = bound == null ? null : bound.getId();

        // إفراغ الحقل فوراً لاستقبال الطالب التالي دون انتظار
        barcodeScannerField.clear();

        FxAsync.supply(() -> attendanceService.processAttendance(barcode, boundSessionId),
                this::showResult,
                // بدون هذا المعالج كان أي خطأ (انقطاع قاعدة البيانات مثلاً) يمر بصمت
                // فيظن الموظف أن القارئ لم يقرأ الكارنيه
                error -> showResult(AttendanceResult.builder()
                        .outcome(AttendanceOutcome.REJECTED)
                        .studentName(I18n.get("common.systemError"))
                        .message(I18n.format("attendance.processFailed", FxAsync.messageOf(error)))
                        .build()));
    }

    private void checkOut(AttendanceLogRow row) {
        FxAsync.supply(() -> attendanceService.checkOut(row.attendanceId()),
                this::showResult,
                error -> Dialogs.error(I18n.get("common.error"), FxAsync.messageOf(error)));
    }

    private void showResult(AttendanceResult result) {
        studentNameLabel.setText(result.getStudentName());
        groupNameLabel.setText(result.getGroupName() == null ? "" : result.getGroupName());
        statusLabel.setText(result.getMessage());
        paintResultCard(result.getOutcome());

        if (result.isSuccess()) {
            upsert(result.getRow());
        } else {
            recordRejection(result);
        }

        java.awt.Toolkit.getDefaultToolkit().beep();

        // مسح البطاقة بعد ثوانٍ استعداداً للطالب التالي؛ والجدول باقٍ فلا يضيع شيء
        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> resetResultCard());
        pause.play();
    }

    /**
     * ثلاثة ألوان لا لونان: الدخول أخضر والانصراف أزرق والرفض أحمر.
     * لونٌ واحد للدخول والانصراف يجعل الموظف يظن أنه سجّل دخول الطالب وقد سجّل خروجه.
     */
    private void paintResultCard(AttendanceOutcome outcome) {
        String background = switch (outcome) {
            case CHECKED_IN -> "#d4edda";
            case CHECKED_OUT -> "#d6eaf8";
            case REJECTED -> "#f8d7da";
        };
        String foreground = switch (outcome) {
            case CHECKED_IN -> "#155724";
            case CHECKED_OUT -> "#1b4f72";
            case REJECTED -> "#721c24";
        };

        resultCard.setStyle("-fx-background-color: " + background + "; -fx-padding: 20; -fx-background-radius: 10;");
        studentNameLabel.setStyle("-fx-text-fill: " + foreground + ";");
        groupNameLabel.setStyle("-fx-text-fill: " + foreground + ";");
        statusLabel.setStyle("-fx-text-fill: " + foreground + ";");
    }

    /** الدخول صفٌّ جديد في أعلى الكشف، والانصراف تحديثٌ لصفٍّ قائم */
    private void upsert(AttendanceLogRow row) {
        if (row == null) return;

        for (int i = 0; i < dayRows.size(); i++) {
            if (dayRows.get(i).attendanceId().equals(row.attendanceId())) {
                dayRows.set(i, row);
                afterRowsChanged();
                return;
            }
        }
        dayRows.add(0, row);
        afterRowsChanged();
    }

    /**
     * عمود الزرّ لا قيمة له ({@code Void})، فتبديل صفٍّ بآخر لا يوقظ خلاياه من نفسه
     * ويبقى زرّ "تسجيل انصراف" معروضاً أمام من انصرف للتوّ.
     */
    private void afterRowsChanged() {
        attendanceLogTable.refresh();
        updateInsideCount();
    }

    private void recordRejection(AttendanceResult result) {
        rejectedScans.add(0, new RejectedScan(LocalTime.now().format(timeFormatter),
                result.getStudentName(), result.getMessage()));
        while (rejectedScans.size() > MAX_REJECTED) {
            rejectedScans.remove(rejectedScans.size() - 1);
        }
    }

    private void updateInsideCount() {
        long inside = dayRows.stream().filter(row -> row.state() == AttendanceState.INSIDE).count();
        insideCountLabel.setText(I18n.format("attendance.insideCount", inside, dayRows.size()));
    }

    private String clock(LocalDateTime moment) {
        return moment == null ? I18n.get("common.empty") : moment.format(timeFormatter);
    }

    private void resetResultCard() {
        studentNameLabel.setText(I18n.get("attendance.waiting"));
        groupNameLabel.setText("");
        statusLabel.setText("");
        resultCard.setStyle("-fx-background-color: #e9ecef; -fx-padding: 20; -fx-background-radius: 10;");
    }

    /** محاولة مرفوضة: لم تُكتب في قاعدة البيانات، فمكانها الذاكرة وحدها */
    @Data
    @AllArgsConstructor
    public static class RejectedScan {
        private String time;
        private String name;
        private String reason;
    }
}
