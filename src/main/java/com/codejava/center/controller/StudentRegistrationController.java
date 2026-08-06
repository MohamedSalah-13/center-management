package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Forms;
import com.codejava.center.util.ViewLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class StudentRegistrationController {

    private final StudentService studentService;
    private final ReportService reportService;
    private final EnrollmentService enrollmentService;
    private final ViewLoader viewLoader;

    private static final String ENROLLMENTS_FXML = "/fxml/StudentEnrollments.fxml";
    private static final double ENROLLMENTS_WIDTH = 900;
    private static final double ENROLLMENTS_HEIGHT = 600;

    @FXML private TextField nameField, phoneField, parentPhoneField, barcodeField;
    @FXML private ComboBox<SchoolLevel> schoolLevelCombo;

    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> colBarcode, colName, colPhone, colLevel;
    @FXML private TableColumn<Student, Void> colEnrollments;
    @FXML private Button updateButton, deleteButton;

    @FXML private TextField searchField;
    @FXML private ComboBox<SchoolLevel> levelFilterCombo;
    @FXML private Label resultCountLabel;

    private final ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private FilteredList<Student> filteredStudents;
    private Student selectedStudent = null;

    @FXML
    public void initialize() {
        // المرحلة قيمة ثابتة تُخزَّن باسمها ويُترجَم عرضها: طالب سُجّل بواجهة إنجليزية
        // تظهر مرحلته بالعربية في الواجهة العربية، ويطابق قيد مجموعة أُنشئت بأيٍّ من اللغتين
        setupLevelCombo();

        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBarcode()));
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        colLevel.setCellValueFactory(d -> new SimpleStringProperty(levelName(d.getValue().getSchoolLevel())));
        setupEnrollmentsColumn();

        setupFilters();
        setupTableSelectionListener();

        loadStudents();
        Forms.numericOnly(phoneField, parentPhoneField);
        Forms.focusNextOnEnter(nameField, phoneField, parentPhoneField);
    }

    /**
     * زر الاشتراكات داخل كل صف.
     *
     * <p>الزر في الصف لا في شريط الأدوات، لنفس سبب زر كشف المجموعة: اشتراكات طالب تُطلب
     * وأنت تنظر إلى سطره. وقسمُ الاشتراكات حين كان داخل الشاشة كان يتبع الصف المحدَّد
     * صامتاً، فيُقرأ "إنهاء الاشتراك" وكأنه يخصّ ما في النموذج لا ما في الجدول.</p>
     */
    private void setupEnrollmentsColumn() {
        colEnrollments.setCellFactory(column -> new TableCell<>() {
            private final Button enrollmentsButton = new Button(I18n.get("student.manageEnrollments"));

            {
                enrollmentsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 2 10;");
                enrollmentsButton.setOnAction(event -> openEnrollments(getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : enrollmentsButton);
            }
        });
    }

    /** نافذة اشتراكات طالب واحد: مستقلة، وتُفتح بالطالب مضبوطاً فيها قبل عرضها */
    private void openEnrollments(Student student) {
        try {
            viewLoader.<StudentEnrollmentsController>showModal(ENROLLMENTS_FXML,
                    I18n.format("student.enrollmentsTitle", student.getName()),
                    studentTable.getScene().getWindow(),
                    ENROLLMENTS_WIDTH, ENROLLMENTS_HEIGHT,
                    controller -> controller.setStudent(student));
        } catch (Exception e) {
            Dialogs.error(I18n.format("student.enrollmentsOpenFailed", FxAsync.messageOf(e)));
        }
    }

    /**
     * تصفية الجدول: نصّ حرّ ومرحلة دراسية.
     *
     * <p>المرحلة تصفية قائمة بذاتها لا كلمة تُكتب في البحث: أسماء المراحل مترجَمة ومتشابهة
     * البدايات، و"الصف الأول" مكتوبة في خانة البحث تُطابق "الصف الأول الثانوي" كما تطابق
     * الإعدادي. والقيمة الفارغة تعني "الكل"، وهي أول الخيارات لا غيابها من القائمة.</p>
     */
    private void setupFilters() {
        levelFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(SchoolLevel level) {
                return level == null ? I18n.get("common.all") : level.getDisplayName();
            }
            @Override
            public SchoolLevel fromString(String string) { return null; }
        });

        List<SchoolLevel> levels = new ArrayList<>();
        levels.add(null);
        levels.addAll(List.of(SchoolLevel.values()));
        levelFilterCombo.getItems().setAll(levels);
        levelFilterCombo.setValue(null);

        filteredStudents = new FilteredList<>(studentsList, student -> true);
        searchField.textProperty().addListener((obs, was, is) -> applyFilters());
        levelFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());

        SortedList<Student> sortedData = new SortedList<>(filteredStudents);
        sortedData.comparatorProperty().bind(studentTable.comparatorProperty());
        studentTable.setItems(sortedData);

        // عدّ المعروض يتبع القائمة نفسها لا الشرط: الحفظ والحذف يغيّران العدد بلا لمس تصفية
        filteredStudents.addListener((ListChangeListener<Student>) change -> refreshResultCount());
        refreshResultCount();
    }

    private void applyFilters() {
        String text = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        SchoolLevel level = levelFilterCombo.getValue();

        filteredStudents.setPredicate(student -> {
            if (level != null && student.getSchoolLevel() != level) {
                return false;
            }
            if (text.isEmpty()) {
                return true;
            }
            return (student.getName() != null && student.getName().toLowerCase().contains(text))
                    || (student.getPhone() != null && student.getPhone().contains(text))
                    || (student.getBarcode() != null && student.getBarcode().toLowerCase().contains(text));
        });
        refreshResultCount();
    }

    private void refreshResultCount() {
        resultCountLabel.setText(I18n.format("student.shownCount",
                filteredStudents.size(), studentsList.size()));
    }

    /** قائمة المراحل: القيم كلها، معروضة بأسمائها المترجَمة */
    private void setupLevelCombo() {
        schoolLevelCombo.getItems().setAll(SchoolLevel.values());
        schoolLevelCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(SchoolLevel level) {
                return levelName(level);
            }
            @Override
            public SchoolLevel fromString(String string) { return null; }
        });
    }

    private String levelName(SchoolLevel level) {
        return level == null ? "" : level.getDisplayName();
    }

    private void setupTableSelectionListener() {
        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedStudent = newVal;
                nameField.setText(selectedStudent.getName());
                phoneField.setText(selectedStudent.getPhone());
                parentPhoneField.setText(selectedStudent.getParentPhone());
                schoolLevelCombo.setValue(selectedStudent.getSchoolLevel());
                barcodeField.setText(selectedStudent.getBarcode());

                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    private void loadStudents() {
        FxAsync.supply(studentService::getAllStudents,
                students -> studentsList.setAll(students),
                error -> Dialogs.error(I18n.format("student.loadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        saveOrUpdateStudent(new Student());
    }

    @FXML
    public void handleUpdateAction(ActionEvent event) {
        if (selectedStudent != null) {
            saveOrUpdateStudent(selectedStudent);
        }
    }

    private void saveOrUpdateStudent(Student student) {
        boolean isNew = student.getId() == null;
        SchoolLevel newLevel = schoolLevelCombo.getValue();

        // تغيير مرحلة طالب مشترك بالفعل: يُفحص قبل لمس الكيان لا بعده. الطالب المحدَّد
        // هو نفس الكائن الموجود في قائمة الجدول، فتعبئته من النموذج ثم التراجع عن الحفظ
        // كانت ستترك بيانات غير محفوظة معروضة في الجدول كأنها محفوظة.
        if (!isNew && student.getSchoolLevel() != newLevel) {
            Student target = student;
            FxAsync.supply(() -> enrollmentService.findEnrolmentsOutsideLevel(target.getId(), newLevel),
                    clashing -> {
                        if (clashing.isEmpty() || confirmLevelChange(target, newLevel, clashing)) {
                            applyFormAndSave(target, false);
                        }
                    },
                    error -> Dialogs.error(FxAsync.messageOf(error)));
            return;
        }

        applyFormAndSave(student, isNew);
    }

    /**
     * تحذير قبل تغيير المرحلة: قيد الصف يُفحص عند الاشتراك، والتغيير بعده لا يمرّ به.
     * تأكيد لا منع - ترقية الصف في أول العام تصرّف مشروع يقع لكل طالب مرة كل سنة.
     */
    private boolean confirmLevelChange(Student student, SchoolLevel newLevel, List<CourseGroup> clashing) {
        String groups = clashing.stream()
                .map(group -> I18n.format("student.levelChangeGroup",
                        group.getName(), group.getSchoolLevel().getDisplayName()))
                .collect(Collectors.joining("\n"));

        return Dialogs.confirm(I18n.get("student.levelChangeTitle"),
                I18n.format("student.levelChangeWarning",
                        student.getName(), groups,
                        newLevel == null ? I18n.get("common.none") : newLevel.getDisplayName()));
    }

    private void applyFormAndSave(Student student, boolean isNew) {
        student.setName(nameField.getText());
        student.setPhone(phoneField.getText());
        student.setParentPhone(parentPhoneField.getText());
        student.setSchoolLevel(schoolLevelCombo.getValue());
        student.setBarcode(barcodeField.getText().isEmpty() ? null : barcodeField.getText());
        student.setActive(true);

        // الحفظ في الخلفية: كان يجري على خيط الواجهة فيجمّد الشاشة حتى ترد قاعدة البيانات
        FxAsync.supply(() -> studentService.saveStudent(student), saved -> {
            if (isNew) {
                studentsList.add(saved);
                Dialogs.success(I18n.format("student.saved", saved.getBarcode()));
            } else {
                // البحث عن الموضع في القائمة المصدر وليس في العرض (الجدول مربوط بـ SortedList/FilteredList
                // ولذلك يختلف ترتيب صفوفه عن studentsList عند الفرز أو البحث)
                int idx = studentsList.indexOf(student);
                if (idx >= 0) {
                    studentsList.set(idx, saved);
                }
                Dialogs.success(I18n.get("common.updated"));
            }
            handleClearAction(null);
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedStudent == null) return;

        if (Dialogs.confirm(I18n.get("common.confirmDelete"),
                I18n.format("student.deleteConfirm", selectedStudent.getName()))) {
            Student target = selectedStudent;
            FxAsync.run(() -> studentService.deleteStudent(target.getId()), () -> {
                studentsList.remove(target);
                handleClearAction(null);
            }, error -> Dialogs.error(I18n.get("student.deleteBlocked")));
        }
    }

    @FXML
    public void handleClearAction(ActionEvent event) {
        nameField.clear();
        phoneField.clear();
        parentPhoneField.clear();
        schoolLevelCombo.setValue(null);
        barcodeField.clear();

        selectedStudent = null;
        studentTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    /**
     * كارنيهات المعروض في الجدول، لا كل من في قاعدة البيانات.
     *
     * <p>نسخة مأخوذة لا القائمة نفسها: التصدير يجري على خيط آخر، وقراءة قائمة الجدول
     * الحيّة من هناك تتعارض مع تعديلها من خيط الواجهة.</p>
     */
    @FXML
    public void handleExportIdCards(ActionEvent event) {
        List<Student> shown = new ArrayList<>(studentTable.getItems());
        if (shown.isEmpty()) {
            Dialogs.warning(I18n.get("student.noStudentsToExport"));
            return;
        }

        // تجهيز مسار الحفظ (مثلاً سطح المكتب) بملف يحمل تاريخ اليوم
        String fileName = "Student_ID_Cards_" + java.time.LocalDate.now();

        FxAsync.supply(() -> {
            try {
                return reportService.exportStudentIdCards(shown, fileName);
            } catch (JRException e) {
                throw new IllegalStateException(FxAsync.messageOf(e), e);
            }
        }, outputPath -> {
            Dialogs.success(I18n.format("student.idCardsExported", outputPath));
            openExported(outputPath);
        }, error -> Dialogs.error(I18n.get("common.exportError"),
                I18n.format("student.idCardsFailed", FxAsync.messageOf(error))));
    }

    /** فتح الملف بعد إنشائه؛ نظامٌ لا يدعم الفتح لا يعني فشل التصدير، فالمسار مذكور سلفاً */
    private void openExported(String outputPath) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(outputPath));
            }
        } catch (IOException e) {
            // المسار معروض في رسالة النجاح، فالمستخدم يصل إليه بيده
        }
    }
}