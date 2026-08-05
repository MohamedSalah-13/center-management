package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Forms;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class StudentRegistrationController {

    private final StudentService studentService;
    private final ReportService reportService;
    // إضافة الخدمات الجديدة
    private final CourseGroupService courseGroupService;
    private final EnrollmentService enrollmentService;

    @FXML private TextField nameField, phoneField, parentPhoneField, barcodeField;
    @FXML private ComboBox<SchoolLevel> schoolLevelCombo;

    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> colBarcode, colName, colPhone, colLevel;
    @FXML private Button updateButton, deleteButton;

    // عناصر واجهة الاشتراك الجديدة
    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private Label groupCapacityLabel;
    @FXML private Label enrollmentHintLabel;
    @FXML private Button subscribeButton;
    @FXML private Button unsubscribeButton;
    @FXML private TextField searchField;

    @FXML private TableView<MembershipRow> membershipsTable;
    @FXML private TableColumn<MembershipRow, String> colMemGroup, colMemJoined, colMemLeft,
            colMemHeld, colMemAttended, colMemRate;

    private final ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private final ObservableList<MembershipRow> memberships = FXCollections.observableArrayList();
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

        studentTable.setItems(studentsList);

        setupGroupComboBox();
        setupMembershipsTable();
        setupTableSelectionListener();

        loadStudents();
        Forms.numericOnly(phoneField, parentPhoneField);
        Forms.focusNextOnEnter(nameField, phoneField, parentPhoneField);


        FilteredList<Student> filteredData = new FilteredList<>(studentsList, b -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(student -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                // البحث بالاسم
                if (student.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                // البحث برقم الهاتف
                else if (student.getPhone() != null && student.getPhone().contains(lowerCaseFilter)) {
                    return true;
                }
                // البحث بالباركود
                else if (student.getBarcode() != null && student.getBarcode().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false; // لا يوجد تطابق
            });
        });

        SortedList<Student> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(studentTable.comparatorProperty());
        studentTable.setItems(sortedData);
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

    private void setupGroupComboBox() {
        // عرض اسم المجموعة في قائمة الاختيار بدلاً من الكائن
        groupComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseGroup group) {
                return group == null ? "" : group.getName();
            }
            @Override
            public CourseGroup fromString(String string) { return null; }
        });

        // مراقبة التغيير في اختيار المجموعة لعرض السعة
        groupComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                FxAsync.supply(() -> enrollmentService.countActiveMembers(newVal), currentStudents -> {
                    groupCapacityLabel.setText(I18n.format("student.capacity",
                            currentStudents, newVal.getMaxCapacity()));

                    // تلوين النص بالأحمر إذا اكتملت السعة
                    if (currentStudents >= newVal.getMaxCapacity()) {
                        groupCapacityLabel.setStyle("-fx-text-fill: #e74c3c;");
                    } else {
                        groupCapacityLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    }
                }, error -> groupCapacityLabel.setText(I18n.get("student.capacityFailed")));
            } else {
                groupCapacityLabel.setText(I18n.get("student.capacityUnknown"));
            }
        });
    }

    /**
     * جدول عضويات الطالب: المجموعة، مدة الاشتراك، وحصادها.
     *
     * <p>الحصص المعروضة هي حصص <b>مدة اشتراكه هو</b> لا حصص المجموعة كلها، ولذلك تصلح
     * "حضر 4 من 5" للحكم على من التحق الأسبوع الماضي كما تصلح لمن معها من أول العام.</p>
     */
    private void setupMembershipsTable() {
        String none = I18n.get("common.none");

        colMemGroup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().groupName()));
        colMemJoined.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().joinDate())));
        colMemLeft.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().active() ? I18n.get("student.membershipActive")
                        : (d.getValue().leaveDate() == null ? none : String.valueOf(d.getValue().leaveDate()))));
        colMemHeld.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().sessionsHeld())));
        colMemAttended.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().sessionsAttended())));
        colMemRate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().attendanceRate() == null ? none : d.getValue().attendanceRate() + "%"));

        membershipsTable.setItems(memberships);
        membershipsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                unsubscribeButton.setDisable(newVal == null || !newVal.active()));
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
                subscribeButton.setDisable(false); // تفعيل زر الاشتراك

                loadGroupsForLevel(selectedStudent.getSchoolLevel());
                loadMemberships(selectedStudent);
            } else {
                subscribeButton.setDisable(true);
                unsubscribeButton.setDisable(true);
                memberships.clear();
                enrollmentHintLabel.setText(I18n.get("student.subscribedGroupsNoStudent"));
            }
        });
    }

    private void loadMemberships(Student student) {
        FxAsync.supply(() -> enrollmentService.getMemberships(student.getId()),
                rows -> {
                    memberships.setAll(rows);
                    unsubscribeButton.setDisable(true);
                },
                error -> {
                    memberships.clear();
                    Dialogs.error(I18n.format("student.groupsLoadFailed", FxAsync.messageOf(error)));
                });
    }

    private void loadStudents() {
        FxAsync.supply(studentService::getAllStudents,
                students -> studentsList.setAll(students),
                error -> Dialogs.error(I18n.format("student.loadFailed", FxAsync.messageOf(error))));
    }

    /**
     * قائمة الاشتراك تعرض مجموعات صف الطالب وحدها.
     *
     * <p>عرض الكل ثم رفض الاختيار برسالة خطأ يجعل الموظف يجرّب حتى يصيب. والقيد نفسه
     * مفروض في {@code EnrollmentService} على أي حال: هذه راحة في الاستخدام لا حماية.</p>
     */
    private void loadGroupsForLevel(SchoolLevel level) {
        groupComboBox.setValue(null);
        groupComboBox.getItems().clear();

        if (level == null) {
            enrollmentHintLabel.setText(I18n.get("student.levelMissingHint"));
            return;
        }

        FxAsync.supply(() -> courseGroupService.getGroupsOfLevel(level),
                groups -> {
                    groupComboBox.getItems().setAll(groups);
                    enrollmentHintLabel.setText(groups.isEmpty()
                            ? I18n.format("student.noGroupsForLevel", level.getDisplayName())
                            : I18n.format("student.groupsOfLevel", level.getDisplayName(), groups.size()));
                },
                error -> Dialogs.error(I18n.format("student.groupsLoadFailed", FxAsync.messageOf(error))));
    }

    // --- دالة الاشتراك في المجموعة الجديدة ---
    @FXML
    public void handleSubscribeAction(ActionEvent event) {
        if (selectedStudent == null || groupComboBox.getValue() == null) {
            Dialogs.warning(I18n.get("student.selectStudentAndGroup"));
            return;
        }

        CourseGroup selectedGroup = groupComboBox.getValue();
        Student target = selectedStudent;

        // فحص السعة والحفظ صارا داخل Transaction واحدة في EnrollmentService،
        // وعدّ السعة بعد النجاح انتقل للخلفية بدل تنفيذه على خيط الواجهة
        FxAsync.supply(() -> {
            enrollmentService.subscribe(target, selectedGroup);
            return enrollmentService.countActiveMembers(selectedGroup);
        }, memberCount -> {
            Dialogs.success(I18n.get("student.subscribed"));
            loadMemberships(target);
            groupCapacityLabel.setText(I18n.format("student.capacity",
                    memberCount, selectedGroup.getMaxCapacity()));
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    /**
     * إنهاء اشتراك: يثبّت يوم الخروج ولا يحذف العضوية.
     * الصف يبقى في الجدول بتاريخيه، لأنه هو ما يحدّ حساب حضور الطالب في تلك المجموعة.
     */
    @FXML
    public void handleUnsubscribeAction(ActionEvent event) {
        MembershipRow row = membershipsTable.getSelectionModel().getSelectedItem();
        if (selectedStudent == null || row == null || !row.active()) {
            Dialogs.warning(I18n.get("student.selectMembership"));
            return;
        }

        if (!Dialogs.confirm(I18n.get("common.confirm"),
                I18n.format("student.unsubscribeConfirm", row.studentName(), row.groupName()))) {
            return;
        }

        Student target = selectedStudent;
        FxAsync.run(() -> enrollmentService.unsubscribe(target.getId(), row.groupId()), () -> {
            Dialogs.success(I18n.get("student.unsubscribed"));
            loadMemberships(target);
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
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
        subscribeButton.setDisable(true);
        unsubscribeButton.setDisable(true);
        groupComboBox.setValue(null);
        groupComboBox.getItems().clear();
        memberships.clear();
        groupCapacityLabel.setText(I18n.get("student.capacityUnknown"));
        enrollmentHintLabel.setText(I18n.get("student.subscribedGroupsNoStudent"));
    }

    @FXML
    public void handleExportIdCards(ActionEvent event) {
        if (studentsList.isEmpty()) {
            Dialogs.warning(I18n.get("student.noStudentsToExport"));
            return;
        }

        // تجهيز مسار الحفظ (مثلاً سطح المكتب) بملف يحمل تاريخ اليوم
        String fileName = "Student_ID_Cards_" + java.time.LocalDate.now().toString();

        CompletableFuture.supplyAsync(() -> {
            try {
                // استدعاء دالة التصدير الموجودة مسبقاً في ReportService
                // يتم تمرير studentsList كمصدر بيانات (DataSource) بدلاً من استعلام قاعدة البيانات
                return reportService.exportReportToPdf(
                        "StudentIdCards.jrxml",
                        new HashMap<>(), // لا توجد بارامترات إضافية نحتاجها هنا
                        studentsList,
                        fileName
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(outputPath -> Platform.runLater(() -> {
            Dialogs.success(I18n.format("student.idCardsExported", outputPath));

            // (اختياري) فتح الملف تلقائياً بعد إنشائه
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(outputPath));
            } catch (Exception e) {
                // تجاهل الخطأ إذا كان نظام التشغيل لا يدعم الفتح التلقائي
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                ex.printStackTrace();
                Dialogs.error(I18n.get("common.exportError"),
                        I18n.format("student.idCardsFailed", FxAsync.messageOf(ex)));
            });
            return null;
        });
    }
}