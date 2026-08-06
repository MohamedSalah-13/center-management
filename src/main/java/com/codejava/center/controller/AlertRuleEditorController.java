package com.codejava.center.controller;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.alert.AlertService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * ضبط قاعدة تنبيه واحدة، في نافذة مستقلة تُفتح من زرّ سطرها في جدول القواعد.
 *
 * <p>القاعدة تُحرَّر في نموذج لا في خلايا الجدول: معنى كل حدّ يختلف باختلاف النوع -
 * "٣" تعني ثلاث غيابات هنا وخمسين جنيهاً هناك - ولا بد أن يُشرح بجواره لحظة كتابته.
 * خليةٌ قابلة للتحرير في جدول لا مكان فيها لهذا الشرح.</p>
 *
 * <p>وكان النموذج لوحاً بجوار الجدول يتبع الصف المحدَّد صامتاً، فيحجز نصف عرض التبويب
 * وهو فارغ حتى يُحدَّد صف، ويُقرأ "حفظ القاعدة" وكأنه يخصّ ما في الجدول لا ما في اللوح.
 * القاعدة تصل عبر {@link #setRule(AlertRule)} قبل عرض النافذة لا عبر المُنشئ: المتحكّم
 * يُبنى من Spring بحقن المُنشئ للخدمات، ولا سبيل لتمرير قيمة وقت التشغيل خلاله.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للنافذة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class AlertRuleEditorController {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AlertService alertService;

    @FXML private Label ruleNameLabel, ruleCategoryLabel, ruleDescriptionLabel, ruleAudienceNoteLabel,
            ruleThresholdLabel, ruleWindowLabel, ruleCooldownLabel, ruleUpdatedLabel;
    @FXML private CheckBox ruleEnabledCheck;
    @FXML private ComboBox<AlertAudience> ruleAudienceCombo;
    @FXML private ComboBox<AlertSeverity> ruleSeverityCombo;
    @FXML private Spinner<Integer> ruleThresholdSpinner, ruleWindowSpinner, ruleCooldownSpinner;
    @FXML private HBox thresholdRow, windowRow, cooldownRow;
    @FXML private Button saveRuleButton;

    private AlertRule rule;

    @FXML
    public void initialize() {
        ruleSeverityCombo.getItems().setAll(AlertSeverity.values());
        ruleSeverityCombo.setConverter(converter(AlertSeverity::getDisplayName));
        ruleAudienceCombo.setConverter(converter(AlertAudience::getDisplayName));

        ruleAudienceCombo.valueProperty().addListener((obs, oldVal, newVal) -> showAudienceNote(newVal));

        configureSpinner(ruleThresholdSpinner, 0, 100000, 0);
        configureSpinner(ruleWindowSpinner, 1, 365, 1);
        configureSpinner(ruleCooldownSpinner, 0, 365, 0);
    }

    /** موضوع النافذة: يُضبط مرة واحدة قبل عرضها، ومنه يُقرأ كل ما تعرضه */
    public void setRule(AlertRule rule) {
        this.rule = rule;

        AlertType type = rule.getType();
        ruleNameLabel.setText(type.getDisplayName());
        ruleCategoryLabel.setText(I18n.get("alerts.category") + " " + type.getCategory().getDisplayName());
        ruleDescriptionLabel.setText(type.getDescription());
        ruleEnabledCheck.setSelected(rule.isEnabled());
        ruleSeverityCombo.setValue(rule.getSeverity());

        // الوجهات المتاحة تتبع النوع: فشل نسخة احتياطية لا يُرسل إلى هاتف ولي أمر،
        // وعرض الخيار ثم رفضه عند الحفظ أسوأ من عدم عرضه
        ruleAudienceCombo.getItems().setAll(type.isParentCapable()
                ? List.of(AlertAudience.values())
                : List.of(AlertAudience.INTERNAL));
        ruleAudienceCombo.setValue(rule.getAudience());

        // المغيِّرات تُخفى لا تُعطَّل: عنوانها يأتي من النوع، وحقل أرقام بجواره عنوان
        // فارغ لا يقول ما هو
        showSpinner(thresholdRow, ruleThresholdLabel, ruleThresholdSpinner, type.usesThreshold(),
                type.getThresholdLabel(), rule.thresholdOrDefault());
        showSpinner(windowRow, ruleWindowLabel, ruleWindowSpinner, type.usesWindow(),
                type.getWindowLabel(), rule.windowDaysOrDefault());

        // تهدئة صفرية تعني نوعاً حَدَثياً يُطلق لحظة وقوعه: لا معنى لضبطها
        boolean scheduled = type.usesThreshold() || type.usesWindow();
        showSpinner(cooldownRow, ruleCooldownLabel, ruleCooldownSpinner, scheduled,
                I18n.get("alerts.ruleCooldown"), rule.cooldownDaysOrDefault());

        ruleUpdatedLabel.setText(rule.getUpdatedAt() == null
                ? I18n.get("alerts.ruleUntouched")
                : I18n.format("alerts.ruleUpdated", rule.getUpdatedAt().format(TIMESTAMP),
                        rule.getUpdatedBy() == null ? I18n.get("audit.systemActor") : rule.getUpdatedBy()));
    }

    private <T> StringConverter<T> converter(Function<T, String> display) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : display.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    /**
     * يضبط مجال الـ Spinner ويربط مُحرِّره بقيمته.
     *
     * <p>الربط ضروري لا تجميلي، وهو نفس ما لزم في شاشة الإعدادات: بدونه لا تصل الكتابة
     * اليدوية إلى قيمة الـ Spinner إلا بعد الضغط على أحد السهمين، فيكتب المستخدم الحدّ
     * الذي يريده ويحفظ فتُحفظ القيمة القديمة. والمُنقِّح يرفض النص الفارغ حتى لا تصير
     * القيمة {@code null}.</p>
     */
    private void configureSpinner(Spinner<Integer> spinner, int min, int max, int initial) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial);
        spinner.setValueFactory(factory);

        TextFormatter<Integer> formatter = new TextFormatter<>(factory.getConverter(), initial,
                change -> change.getControlNewText().matches("[0-9]+") ? change : null);
        spinner.getEditor().setTextFormatter(formatter);
        factory.valueProperty().bindBidirectional(formatter.valueProperty());
    }

    /**
     * يعرض صفّ مغيِّرٍ أو يطويه بكامله.
     *
     * <p>{@code managed} على الصفّ لا على عنوانه ومغيِّره: صندوق الترتيب يحسب مسافته
     * بين ما يُدير وحده، فطيّ الصفّ يطوي المسافة معه. وإخفاء العنصرين داخل صفٍّ باقٍ
     * كان يترك سطراً فارغاً بارتفاعه ومسافته يقول إن شيئاً كان هنا.</p>
     */
    private void showSpinner(HBox row, Label label, Spinner<Integer> spinner, boolean shown,
                             String text, int value) {
        label.setText(text);
        row.setVisible(shown);
        row.setManaged(shown);

        if (shown) {
            spinner.getValueFactory().setValue(value);
        }
    }

    /**
     * التحذير يُلوَّن بصنف من ملف التنسيق لا بلون مكتوب هنا.
     *
     * <p>{@code setStyle} يكتب لوناً ثابتاً في الكود بجوار لوحة ألوان الملف، فيصير
     * تغيير أحمر التطبيق تغييراً في موضعين. و{@code removeAll} قبل الإضافة ضروري:
     * الصنفان يتبادلان مع كل تغيير للوجهة، وبلا حذفٍ يتراكمان فيغلب أولهما.</p>
     */
    private void showAudienceNote(AlertAudience audience) {
        boolean toParents = audience != null && audience.includesParents();
        ruleAudienceNoteLabel.setText(toParents
                ? I18n.get("alerts.audienceParentsNote") : I18n.get("alerts.audienceInternalNote"));

        ruleAudienceNoteLabel.getStyleClass().removeAll("muted-text", "danger-text");
        ruleAudienceNoteLabel.getStyleClass().add(toParents ? "danger-text" : "muted-text");
    }

    /**
     * حفظ ضبط القاعدة.
     *
     * <p>تأكيدٌ صريح حين تصير الوجهة أولياء الأمور: هذه هي اللحظة التي يبدأ فيها
     * البرنامج بمراسلة أرقام حقيقية باسم السنتر بلا ضغطة من موظف. الشاشة تقول ذلك
     * بصريح العبارة لأن ما بعده لا يمكن سحبه.</p>
     */
    @FXML
    public void handleSaveRule(ActionEvent event) {
        AlertType type = rule.getType();
        AlertAudience audience = ruleAudienceCombo.getValue();
        boolean startsMessagingParents = ruleEnabledCheck.isSelected()
                && audience != null && audience.includesParents()
                && !rule.notifiesParents();

        if (startsMessagingParents && !Dialogs.confirm(I18n.get("alerts.parentConfirmTitle"),
                I18n.format("alerts.parentConfirm", type.getDisplayName()))) {
            return;
        }

        AlertRule edited = AlertRule.builder()
                .type(type)
                .enabled(ruleEnabledCheck.isSelected())
                .audience(audience == null ? AlertAudience.INTERNAL : audience)
                .severity(ruleSeverityCombo.getValue() == null
                        ? type.getDefaultSeverity() : ruleSeverityCombo.getValue())
                .threshold(type.usesThreshold() ? ruleThresholdSpinner.getValue() : null)
                .windowDays(type.usesWindow() ? ruleWindowSpinner.getValue() : null)
                .cooldownDays(ruleCooldownSpinner.isManaged()
                        ? ruleCooldownSpinner.getValue() : type.getDefaultCooldownDays())
                .build();

        saveRuleButton.setDisable(true);
        FxAsync.supply(() -> alertService.saveRule(edited), saved -> {
            Dialogs.success(I18n.get("common.updated"));
            close();
        }, error -> {
            saveRuleButton.setDisable(false);
            Dialogs.error(FxAsync.messageOf(error));
        });
    }

    @FXML
    public void handleCloseAction(ActionEvent event) {
        close();
    }

    private void close() {
        ((Stage) saveRuleButton.getScene().getWindow()).close();
    }
}
