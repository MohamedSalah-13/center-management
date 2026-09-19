package com.codejava.center.controller;

import com.codejava.center.domain.enums.Role;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Shortcut;
import com.codejava.center.util.ShortcutAction;
import com.codejava.center.util.ShortcutPreferences;
import com.codejava.center.util.Shortcuts;
import com.codejava.center.util.UserSession;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * شاشة أزرار الوصول السريع: أي تركيبة مفاتيح تفتح أي أمر على هذا الجهاز.
 *
 * <p><b>التركيبة تُلتقط من لوحة المفاتيح، ولا تُكتب في حقل.</b> حقل نصّ يعني أن يكتب
 * المستخدم "Ctrl+ص" أو "كنترول+S" فتُقرأ إحداهما ولا تُقرأ الأخرى، وأن يظنّ أن ما ضبطه
 * يعمل حتى يجرّبه. وبالالتقاط يكون ما رآه هو ما ضغطه بالضبط - وهو أيضاً ما يجعل تخطيط
 * لوحة عربية أو مفتاحاً غير قياسي مسألةً لا وجود لها هنا.</p>
 *
 * <p><b>ولا تُقبل تركيبة مشغولة.</b> {@code Scene} لا تعترض على أمرين تحت مفتاح واحد؛ هي
 * تمرّ على جدول اختصاراتها وتنفّذ أول ما يطابق، فالنتيجة أمرٌ يعمل وآخر يُقرأ معطّلاً بلا
 * أن يقول أحدٌ لماذا. الرفض هنا - في اللحظة التي ضُغطت فيها التركيبة، وباسم الأمر الذي
 * يحملها - هو الموضع الوحيد الذي يستطيع أن يقول السبب.</p>
 *
 * <p>ولكل الأدوار، كاللغة وحجم الخط: شاشة الإعدادات للمدير وحده، ومن يجلس على المكتب
 * طوال اليوم هو أوّل من ينتفع بمفتاحٍ يفتح شاشة الحضور. والجدول لا يعرض إلا ما يسمح به
 * دور من فتحه - {@code Ctrl+B} على جهازٍ يجلس عليه السكرتير لا يطلق نسخة احتياطية.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class ShortcutsController {

    private final UserSession userSession;

    @FXML private VBox root;
    @FXML private TableView<ShortcutRow> shortcutTable;
    @FXML private TableColumn<ShortcutRow, String> colAction;
    @FXML private TableColumn<ShortcutRow, String> colShortcut;
    @FXML private TableColumn<ShortcutRow, ShortcutRow> colEdit;
    @FXML private Label captureLabel;
    @FXML private Label statusLabel;

    private final ObservableList<ShortcutRow> rows = FXCollections.observableArrayList();

    /**
     * الحالة المعروضة لكل الأوامر، لا للمعروض منها وحده.
     *
     * <p>فحص التعارض يجري على هذه الخريطة: أمرٌ لا يراه السكرتير يبقى محجوزاً على تركيبته،
     * وإلا أسند {@code Ctrl+B} إلى شاشة الحضور ثم وجد المديرُ التركيبتين حيّتين معاً على
     * نفس الجهاز.</p>
     */
    private final Map<ShortcutAction, Shortcut> bindings = new EnumMap<>(ShortcutAction.class);

    private ShortcutRow capturing;
    private Scene captureScene;
    private EventHandler<KeyEvent> captureFilter;

    @FXML
    public void initialize() {
        setupTable();
        load();

        // مغادرة الشاشة أثناء التسجيل: الاختصارات مفكوكة ومرشِّح الأحداث ما زال على
        // المشهد، فبدون هذا يخرج المستخدم من الشاشة ولا يعود أي اختصار يعمل
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                endCapture();
            }
        });
    }

    private void setupTable() {
        colAction.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getActionName()));
        colShortcut.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getShortcutText()));
        colEdit.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        colEdit.setCellFactory(column -> new EditCell());

        shortcutTable.setItems(rows);
    }

    /** يقرأ المحفوظ على الجهاز ويعرض منه ما يسمح به دور المستخدم الحالي */
    private void load() {
        bindings.clear();
        bindings.putAll(ShortcutPreferences.load());

        Role role = userSession.getCurrentUser() == null ? null : userSession.getCurrentUser().getRole();

        List<ShortcutRow> visible = new ArrayList<>();
        for (ShortcutAction action : ShortcutAction.values()) {
            if (action.isAllowedFor(role)) {
                visible.add(new ShortcutRow(action, bindings.get(action)));
            }
        }
        rows.setAll(visible);

        captureLabel.setText(I18n.get("shortcuts.hint"));
        shortcutTable.refresh();
    }

    // ================================================================ التسجيل

    /**
     * يبدأ التقاط التركيبة لصفٍّ بعينه.
     *
     * <p>وأول ما يفعله فكّ الاختصارات عن المشهد: لولا ذلك لفتحت {@code Ctrl+S} شاشةَ
     * الإعدادات في اللحظة التي يحاول فيها المستخدم إسنادها إلى أمر آخر، فلا يبقى سبيل
     * لإعادة ضبط أي اختصار مستعمَل.</p>
     */
    private void beginCapture(ShortcutRow row) {
        endCapture();

        captureScene = root.getScene();
        if (captureScene == null) {
            return;
        }

        capturing = row;
        Shortcuts.suspend(captureScene);

        captureFilter = this::onCaptureKey;
        // مرشِّح لا معالِج: المرشِّح يمرّ على الحدث قبل أن يصل إلى أي حقل أو زر في الشاشة
        captureScene.addEventFilter(KeyEvent.KEY_PRESSED, captureFilter);

        captureLabel.setText(I18n.format("shortcuts.capturing", row.getActionName()));
        statusLabel.setText("");
        shortcutTable.refresh();
    }

    private void onCaptureKey(KeyEvent event) {
        event.consume();

        KeyCode code = event.getCode();

        // Ctrl وحده ليس اختيار المستخدم بل نصف طريقه إليه: الانتظار حتى يصل المفتاح
        if (code == null || code.isModifierKey()) {
            return;
        }

        if (code == KeyCode.ESCAPE && !event.isControlDown() && !event.isShiftDown() && !event.isAltDown()) {
            endCapture();
            return;
        }

        Shortcut candidate = new Shortcut(code,
                event.isControlDown(), event.isShiftDown(), event.isAltDown());

        // الرفض لا يُنهي التسجيل: المستخدم ما زال يريد اختصاراً لهذا الصف، وإنهاؤه
        // يجعله يضغط الزر من جديد بعد كل محاولة
        if (!candidate.isValid()) {
            captureLabel.setText(I18n.get("shortcuts.needsModifier"));
            return;
        }
        if (candidate.isReserved()) {
            captureLabel.setText(I18n.format("shortcuts.reserved", candidate.display()));
            return;
        }

        ShortcutAction conflict = Shortcuts.conflictOf(bindings, capturing.getAction(), candidate);
        if (conflict != null) {
            captureLabel.setText(I18n.format("shortcuts.conflict",
                    candidate.display(), conflict.getDisplayName()));
            return;
        }

        assign(capturing, candidate);
        endCapture();
    }

    /** يُنهي التسجيل - إن كان جارياً - ويعيد ربط الاختصارات المحفوظة بالمشهد */
    private void endCapture() {
        if (captureScene != null && captureFilter != null) {
            captureScene.removeEventFilter(KeyEvent.KEY_PRESSED, captureFilter);
            Shortcuts.refresh(captureScene);
        }

        capturing = null;
        captureFilter = null;
        captureScene = null;

        if (captureLabel != null) {
            captureLabel.setText(I18n.get("shortcuts.hint"));
            shortcutTable.refresh();
        }
    }

    private void assign(ShortcutRow row, Shortcut shortcut) {
        row.setShortcut(shortcut);
        if (shortcut == null) {
            bindings.remove(row.getAction());
        } else {
            bindings.put(row.getAction(), shortcut);
        }
        statusLabel.setText(I18n.get("shortcuts.unsaved"));
        shortcutTable.refresh();
    }

    // ================================================================ الحفظ

    @FXML
    public void handleSave(ActionEvent event) {
        endCapture();

        // ما تعرضه الشاشة وحده: الحفظ الجزئي هو ما يمنع أن يمحو دخولُ السكرتير مرةً
        // واحدة اختصاراتِ المدير كلها من هذا الجهاز
        Map<ShortcutAction, Shortcut> toSave = new EnumMap<>(ShortcutAction.class);
        rows.forEach(row -> toSave.put(row.getAction(), row.getShortcut()));

        try {
            ShortcutPreferences.save(toSave);
        } catch (RuntimeException e) {
            Dialogs.error(I18n.get("shortcuts.saveFailed"), e.getMessage());
            return;
        }

        // فوراً لا بعد إعادة التشغيل: اختصارٌ يُحفظ ولا يعمل يُقرأ شاشةً لا تحفظ
        Shortcuts.refresh(root.getScene());
        statusLabel.setText(I18n.get("shortcuts.saved"));
    }

    @FXML
    public void handleRestoreDefaults(ActionEvent event) {
        endCapture();

        if (!Dialogs.confirm(I18n.get("shortcuts.restoreConfirm"))) {
            return;
        }

        try {
            ShortcutPreferences.resetAll();
        } catch (RuntimeException e) {
            Dialogs.error(I18n.get("shortcuts.saveFailed"), e.getMessage());
            return;
        }

        load();
        Shortcuts.refresh(root.getScene());
        statusLabel.setText(I18n.get("shortcuts.restored"));
    }

    // ================================================================ الصف وخليته

    /** خلية الأزرار: "تغيير" تبدأ التسجيل، و"مسح" تترك الأمر بلا اختصار */
    private class EditCell extends TableCell<ShortcutRow, ShortcutRow> {

        private final Button changeButton = new Button();
        private final Button clearButton = new Button(I18n.get("shortcuts.clear"));
        private final HBox box = new HBox(8, changeButton, clearButton);

        EditCell() {
            box.setAlignment(Pos.CENTER_LEFT);
            changeButton.getStyleClass().add("btn-secondary");
            clearButton.getStyleClass().add("btn-secondary");

            changeButton.setOnAction(event -> beginCapture(getItem()));
            clearButton.setOnAction(event -> {
                endCapture();
                assign(getItem(), null);
            });
        }

        @Override
        protected void updateItem(ShortcutRow item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            boolean recording = item == capturing;
            changeButton.setText(I18n.get(recording ? "shortcuts.pressKeys" : "shortcuts.change"));
            clearButton.setDisable(item.getShortcut() == null);
            setGraphic(box);
        }
    }

    /** صف الجدول: أمرٌ وتركيبته المعروضة الآن (قد تكون معدَّلة ولم تُحفظ بعد) */
    public static class ShortcutRow {

        private final ShortcutAction action;
        private Shortcut shortcut;

        ShortcutRow(ShortcutAction action, Shortcut shortcut) {
            this.action = action;
            this.shortcut = shortcut;
        }

        public ShortcutAction getAction() {
            return action;
        }

        public Shortcut getShortcut() {
            return shortcut;
        }

        void setShortcut(Shortcut shortcut) {
            this.shortcut = shortcut;
        }

        public String getActionName() {
            return action.getDisplayName();
        }

        /** الفراغ يُكتب صراحةً: خانة خالية تُقرأ عطلاً في العرض لا أمراً بلا اختصار */
        public String getShortcutText() {
            return shortcut == null ? I18n.get("shortcuts.unbound") : shortcut.display();
        }
    }
}
