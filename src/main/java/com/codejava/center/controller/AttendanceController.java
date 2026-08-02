package com.codejava.center.controller;

import com.codejava.center.domain.Session;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.dto.AttendanceResult;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class AttendanceController {

    // سيتم حقن خدمة الحضور لاحقاً
     private final AttendanceService attendanceService;
     private final SessionService sessionService;

    @FXML private ComboBox<Session> sessionComboBox;
    @FXML private TextField barcodeScannerField;
    @FXML private VBox resultCard;
    @FXML private Label studentNameLabel;
    @FXML private Label groupNameLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<AttendanceLog> attendanceLogTable;
    @FXML private TableColumn<AttendanceLog, String> colTime;
    @FXML private TableColumn<AttendanceLog, String> colName;
    @FXML private TableColumn<AttendanceLog, String> colStatus;

    private final ObservableList<AttendanceLog> logList = FXCollections.observableArrayList();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a");

    @FXML
    public void initialize() {
        setupSessionComboBox();

        // إعداد أعمدة الجدول
        colTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTime()));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        attendanceLogTable.setItems(logList);

        // الاحتفاظ بالتركيز: إعادة التركيز لحقل الباركود دائماً
        Platform.runLater(() -> barcodeScannerField.requestFocus());
        barcodeScannerField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                Platform.runLater(() -> barcodeScannerField.requestFocus());
            }
        });

        resetResultCard();
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
                        ? "تلقائي (حسب مجموعة الطالب)"
                        : session.getGroup().getName() + " - " + session.getSessionDate();
            }

            @Override
            public Session fromString(String string) {
                return null;
            }
        });

        CompletableFuture.supplyAsync(sessionService::getActiveSessions)
                .thenAccept(sessions -> Platform.runLater(() -> sessionComboBox.getItems().setAll(sessions)))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
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

        // تنفيذ عملية البحث والتحقق في مسار (Thread) منفصل لعدم تجميد الواجهة
        // داخل دالة handleBarcodeScan:
        CompletableFuture.supplyAsync(() -> {
            // استدعاء الخدمة الفعلي
            AttendanceResult result = attendanceService.processAttendance(barcode, boundSessionId);

            // تحويل النتيجة إلى DTO الخاص بالجدول لعرضه في الواجهة
            String timeStr = LocalTime.now().format(timeFormatter);
            return new AttendanceLog(
                    timeStr,
                    result.getStudentName(),
                    result.getMessage(),
                    result.isSuccess() // يمكننا إضافة حالة النجاح لـ AttendanceLog للتحكم بالألوان
            );
        }).thenAccept(logResult -> {
            Platform.runLater(() -> updateUIWithResult(logResult));
        }).exceptionally(ex -> {
            // بدون هذا المعالج كان أي خطأ (انقطاع قاعدة البيانات مثلاً) يمر بصمت
            // فيظن الموظف أن القارئ لم يقرأ الكارنيه
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            Platform.runLater(() -> updateUIWithResult(new AttendanceLog(
                    LocalTime.now().format(timeFormatter),
                    "خطأ في النظام",
                    "تعذر إتمام العملية: " + message,
                    false
            )));
            return null;
        });
    }

    private void updateUIWithResult(AttendanceLog result) {
        studentNameLabel.setText(result.getName());
        statusLabel.setText(result.getStatus());

        // الاعتماد على قيمة النجاح المنطقية القادمة من الخدمة وليس على نص الرسالة
        if (result.isSuccess()) {
            // حالة النجاح: لون أخضر
            resultCard.setStyle("-fx-background-color: #d4edda; -fx-padding: 20; -fx-background-radius: 10;");
            studentNameLabel.setStyle("-fx-text-fill: #155724;");
            statusLabel.setStyle("-fx-text-fill: #155724;");

            // إطلاق تنبيه صوتي للنجاح
            java.awt.Toolkit.getDefaultToolkit().beep();
        } else {
            // حالة الرفض: لون أحمر
            resultCard.setStyle("-fx-background-color: #f8d7da; -fx-padding: 20; -fx-background-radius: 10;");
            studentNameLabel.setStyle("-fx-text-fill: #721c24;");
            statusLabel.setStyle("-fx-text-fill: #721c24;");

            // يمكنك هنا إطلاق نفس الصوت، أو استخدام مكتبة JavaFX Media لتشغيل ملف MP3/WAV مخصص للخطأ
            java.awt.Toolkit.getDefaultToolkit().beep();
        }

        // إضافة السجل في أعلى الجدول
        logList.add(0, result);

        // --- إضافة ميزة مسح الشاشة بعد 3 ثوانٍ ---
        // نستخدم PauseTransition لتنفيذ كود معين بعد مرور وقت محدد
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(e -> resetResultCard());
        pause.play();
    }

    private void resetResultCard() {
        studentNameLabel.setText("في انتظار قراءة الكارنيه...");
        groupNameLabel.setText("");
        statusLabel.setText("");
        resultCard.setStyle("-fx-background-color: #e9ecef; -fx-padding: 20; -fx-background-radius: 10;");
    }

    // كلاس مؤقت لحمل بيانات السجل (DTO)
    @Data
    @AllArgsConstructor
    public static class AttendanceLog {
        private String time;
        private String name;
        private String status;
        private boolean success;
    }
}