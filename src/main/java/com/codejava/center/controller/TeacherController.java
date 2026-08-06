package com.codejava.center.controller;

import com.codejava.center.domain.Teacher;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TeacherService;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Sheets;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.Forms;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class TeacherController {

    private static final String[] COMMISSION_TYPES = {"PERCENTAGE", "FIXED_AMOUNT", "RENT"};

    private final ReportService reportService;
    private final TeacherService teacherService;
    private final ObservableList<Teacher> teachersList = FXCollections.observableArrayList();
    @FXML
    private TextField nameField, subjectField, valueField;
    @FXML private TextField searchField;
    @FXML
    private ComboBox<String> typeCombo;
    @FXML private ComboBox<String> subjectFilterCombo, typeFilterCombo;
    @FXML
    private TableView<Teacher> teacherTable;
    @FXML
    private TableColumn<Teacher, String> colName, colSubject, colType, colValue;
    @FXML private TableColumn<Teacher, Void> colPrint;
    @FXML private Button updateButton, deleteButton;
    private Teacher selectedTeacher = null;

    private FilteredList<Teacher> filteredTeachers;

    @FXML
    public void initialize() {
        // القيمة المخزَّنة تبقى الرمز الإنجليزي؛ المعروض وحده هو المترجَم،
        // وإلا لأصبح ما يُحفظ في قاعدة البيانات تابعاً للغة الشاشة وقت الإدخال
        typeCombo.getItems().addAll(COMMISSION_TYPES);
        typeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String type) {
                return type == null ? "" : CommissionTypes.displayName(type);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });

        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        colSubject.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSubject()));
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                CommissionTypes.displayName(d.getValue().getCommissionType())));
        colValue.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(MoneyUtils.format(d.getValue().getCommissionValue())));
        setupPrintColumn();

        setupFilters();
        setupTableSelectionListener();
        loadTeachers();

        // تأمين خانة المبلغ
        Forms.decimalOnly(valueField);
        Forms.focusNextOnEnter(nameField, subjectField);
    }

    /**
     * زر كشف الحساب داخل كل صف.
     *
     * <p>الزر في الصف لا في شريط النموذج: كشف حساب معلم يُطلب وأنت تنظر إلى سطره، وزرٌّ
     * واحد يطبع "المحدَّد" يعني تحديداً - يملأ نموذج التعديل بلا حاجة - ثم بحثاً عن الزر
     * ثم تأكداً من أن الصف المحدَّد هو المقصود. وكشف حساب المعلم الخطأ يُصرف عليه.</p>
     */
    private void setupPrintColumn() {
        colPrint.setCellFactory(column -> new TableCell<>() {
            private final Button printButton = new Button(I18n.get("teacher.printStatement"));

            {
                printButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-padding: 2 10;");
                printButton.setOnAction(event -> printStatement(getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : printButton);
            }
        });
    }

    /**
     * تصفية الجدول: بحثٌ نصّي، ومادة، ونوع عمولة.
     *
     * <p>القيمة الفارغة في القائمتين تعني "الكل"، وهي أول الخيارات لا غيابها منها: قائمة
     * لا خيار فيها للعودة إلى كل المعلمين تعني إعادة فتح الشاشة لإلغاء تصفية.</p>
     */
    private void setupFilters() {
        subjectFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String subject) {
                return subject == null ? I18n.get("common.all") : subject;
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        typeFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String type) {
                return type == null ? I18n.get("common.all") : CommissionTypes.displayName(type);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });

        List<String> types = new ArrayList<>();
        types.add(null);
        types.addAll(List.of(COMMISSION_TYPES));
        typeFilterCombo.getItems().setAll(types);

        subjectFilterCombo.setValue(null);
        typeFilterCombo.setValue(null);

        filteredTeachers = new FilteredList<>(teachersList, teacher -> true);
        searchField.textProperty().addListener((obs, was, is) -> applyFilters());
        subjectFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());
        typeFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());

        SortedList<Teacher> sortedData = new SortedList<>(filteredTeachers);
        sortedData.comparatorProperty().bind(teacherTable.comparatorProperty());
        teacherTable.setItems(sortedData);
    }

    private void applyFilters() {
        String text = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String subject = subjectFilterCombo.getValue();
        String type = typeFilterCombo.getValue();

        filteredTeachers.setPredicate(teacher -> {
            if (!text.isEmpty()
                    && !teacher.getName().toLowerCase().contains(text)
                    && !teacher.getSubject().toLowerCase().contains(text)) {
                return false;
            }
            if (subject != null && !subject.equals(teacher.getSubject())) {
                return false;
            }
            return type == null || type.equals(teacher.getCommissionType());
        });
    }

    /** المواد المتاحة تُبنى من المعلمين أنفسهم: قائمة مواد ثابتة تعرض مواد لا معلّم لها */
    private void refreshSubjectFilterItems() {
        String chosen = subjectFilterCombo.getValue();

        List<String> subjects = new ArrayList<>();
        subjects.add(null);
        teachersList.stream()
                .map(Teacher::getSubject)
                .filter(Objects::nonNull)
                .filter(subject -> !subject.isBlank())
                .distinct()
                .sorted()
                .forEach(subjects::add);

        subjectFilterCombo.getItems().setAll(subjects);
        subjectFilterCombo.setValue(subjects.contains(chosen) ? chosen : null);
    }

    private void setupTableSelectionListener() {
        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTeacher = newVal;
                nameField.setText(selectedTeacher.getName());
                subjectField.setText(selectedTeacher.getSubject());
                typeCombo.setValue(selectedTeacher.getCommissionType());
                valueField.setText(MoneyUtils.format(selectedTeacher.getCommissionValue()));

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    /**
     * كشف حساب معلم واحد: يُقرأ ويُبنى ويُسلَّم كلُّه في الخلفية.
     *
     * <p>قراءة الحصص وملء الورقة في نفس الاستدعاء لا في اثنين: الأول وحده كان يعود إلى خيط
     * الواجهة ليُطبع عليه، لأن {@code PrinterJob} في مسار JavaFX لا يعمل خارجه. وجاسبر لا
     * يشترط ذلك، فلا شيء يعود إلى خيط الواجهة إلا نتيجة التسليم.</p>
     */
    private void printStatement(Teacher teacher) {
        FxAsync.supply(() -> reportService.deliverTeacherStatement(teacher,
                        teacherService.getPayableSessionsOf(teacher.getId())),
                Sheets::show,
                error -> Dialogs.error(I18n.format("teacher.printFailed", FxAsync.messageOf(error))));
    }

    /** كشف بكل المعلمين المعروضين بعد التصفية - كل المعلمين، أو مادة بعينها، أو نوع اتفاق بعينه */
    @FXML
    public void handlePrintFilteredAction(javafx.event.ActionEvent event) {
        List<Teacher> shown = new ArrayList<>(teacherTable.getItems());
        if (shown.isEmpty()) {
            Dialogs.warning(I18n.get("teacher.nothingToPrint"));
            return;
        }

        // نسخة من الصفوف ووصف التصفية تُؤخذ هنا: البناء يجري في الخلفية، وقراءة قائمة
        // الجدول الحيّة من هناك تتعارض مع تعديلها من خيط الواجهة
        String filters = describeFilters();

        FxAsync.supply(() -> reportService.deliverTeachersList(shown, filters),
                Sheets::show,
                error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
    }

    /** وصف التصفية كما يُطبع في ترويسة الكشف */
    private String describeFilters() {
        List<String> parts = new ArrayList<>();
        if (subjectFilterCombo.getValue() != null) {
            parts.add(I18n.format("teacher.filterSubjectAs", subjectFilterCombo.getValue()));
        }
        if (typeFilterCombo.getValue() != null) {
            parts.add(I18n.format("teacher.filterTypeAs",
                    CommissionTypes.displayName(typeFilterCombo.getValue())));
        }
        if (searchField.getText() != null && !searchField.getText().isBlank()) {
            parts.add(I18n.format("teacher.filterSearchAs", searchField.getText().trim()));
        }

        return parts.isEmpty()
                ? I18n.get("teacher.filterAll")
                : String.join(I18n.get("common.listSeparator") + " ", parts);
    }

    @FXML
    private void clearFields() {
        nameField.clear();
        subjectField.clear();
        valueField.clear();
        typeCombo.setValue(null);

        selectedTeacher = null;
        teacherTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void loadTeachers() {
        FxAsync.supply(teacherService::getAllTeachers,
                teachers -> {
                    teachersList.setAll(teachers);
                    refreshSubjectFilterItems();
                },
                error -> Dialogs.error(I18n.format("teacher.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleSaveAction() {
        saveOrUpdateTeacher(new Teacher());
    }

    @FXML
    public void handleUpdateAction() {
        if (selectedTeacher != null) {
            saveOrUpdateTeacher(selectedTeacher);
        }
    }

    private void saveOrUpdateTeacher(Teacher teacher) {
        try {
            teacher.setName(nameField.getText());
            teacher.setSubject(subjectField.getText());
            teacher.setCommissionType(typeCombo.getValue());
            teacher.setCommissionValue(new BigDecimal(valueField.getText().trim()));
        } catch (NumberFormatException e) {
            Dialogs.error(I18n.get("common.invalidInput"), I18n.get("teacher.commissionMustBeNumeric"));
            return;
        }

        boolean isNew = teacher.getId() == null;

        FxAsync.supply(() -> teacherService.saveTeacher(teacher), saved -> {
            if (isNew) {
                teachersList.add(saved); // إضافة جديد
            } else {
                // البحث عن الموضع في القائمة المصدر وليس في العرض (الجدول مربوط بـ SortedList/FilteredList
                // ولذلك يختلف ترتيب صفوفه عن teachersList عند الفرز أو البحث)
                int idx = teachersList.indexOf(teacher);
                if (idx >= 0) {
                    teachersList.set(idx, saved); // تحديث صف موجود
                }
            }
            refreshSubjectFilterItems();
            clearFields();
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    @FXML
    public void handleDeleteAction() {
        if (selectedTeacher == null) return;

        if (Dialogs.confirm(I18n.get("common.confirmDelete"),
                I18n.format("teacher.deleteConfirm", selectedTeacher.getName()))) {
            Teacher target = selectedTeacher;
            FxAsync.run(() -> teacherService.deleteTeacher(target.getId()), () -> {
                teachersList.remove(target);
                refreshSubjectFilterItems();
                clearFields();
            }, error -> Dialogs.error(I18n.get("teacher.deleteBlocked")));
        }
    }

}