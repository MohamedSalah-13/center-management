package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PrintPreferences;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * إدارة اشتراكات طالب واحد، في نافذة مستقلة تُفتح من سطره في جدول الطلاب.
 *
 * <p>كانت هذه اللوحة قسماً داخل شاشة التسجيل يتبع الصف المحدَّد: نموذج البيانات والاشتراكات
 * وجدول الطلاب في شاشة واحدة، فيُقرأ "إنهاء الاشتراك" وكأنه يخصّ ما في النموذج لا ما في
 * الجدول. النافذة تُفتح باسم طالب مكتوب في ترويستها، فلا يبقى سؤال عمّن يُشترك.</p>
 *
 * <p>الطالب يصل عبر {@link #setStudent(Student)} قبل عرض النافذة لا عبر المُنشئ: المتحكّم
 * يُبنى من Spring بحقن المُنشئ للخدمات، ولا سبيل لتمرير قيمة وقت التشغيل خلاله.</p>
 */
@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للنافذة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class StudentEnrollmentsController {

    private final CourseGroupService courseGroupService;
    private final EnrollmentService enrollmentService;
    private final ReportService reportService;

    @FXML private Label studentNameLabel;
    @FXML private Label studentLevelLabel;
    @FXML private Label studentSummaryLabel;
    @FXML private ComboBox<CourseGroup> groupComboBox;
    @FXML private Label groupCapacityLabel;
    @FXML private Label enrollmentHintLabel;
    @FXML private Button subscribeButton;
    @FXML private Button unsubscribeButton;
    @FXML private Button printButton;

    @FXML private TableView<MembershipRow> membershipsTable;
    @FXML private TableColumn<MembershipRow, String> colMemGroup, colMemJoined, colMemLeft,
            colMemHeld, colMemAttended, colMemRate;

    private final ObservableList<MembershipRow> memberships = FXCollections.observableArrayList();
    private Student student;

    @FXML
    public void initialize() {
        // معطَّل حتى تصل العضويات: زرّ طباعة يعمل قبل أن يُعرض شيء يُخرج ورقة فارغة
        printButton.setDisable(true);
        setupGroupComboBox();
        setupMembershipsTable();
    }

    /** موضوع النافذة: يُضبط مرة واحدة قبل عرضها، ومنه يُقرأ كل ما تعرضه */
    public void setStudent(Student student) {
        this.student = student;
        studentNameLabel.setText(student.getName());
        studentLevelLabel.setText(I18n.format("student.enrollmentsLevel",
                student.getSchoolLevel() == null
                        ? I18n.get("common.none") : student.getSchoolLevel().getDisplayName()));

        loadGroupsForLevel(student.getSchoolLevel());
        loadMemberships();
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

                    // اكتمال السعة يُقال بالأحمر - وبصنف من التنسيق لا بلون مكتوب هنا،
                    // وإلا بقي رقماً لا يتبع لوحة ألوان البرنامج حين تتغيّر
                    markFull(newVal.getMaxCapacity() != null && currentStudents >= newVal.getMaxCapacity());
                }, error -> {
                    groupCapacityLabel.setText(I18n.get("student.capacityFailed"));
                    markFull(false);
                });
            } else {
                groupCapacityLabel.setText(I18n.get("student.capacityUnknown"));
                markFull(false);
            }
        });
    }

    private void markFull(boolean full) {
        groupCapacityLabel.getStyleClass().remove("danger-text");
        if (full) {
            groupCapacityLabel.getStyleClass().add("danger-text");
        }
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

    private void loadMemberships() {
        FxAsync.supply(() -> enrollmentService.getMemberships(student.getId()),
                rows -> {
                    memberships.setAll(rows);
                    unsubscribeButton.setDisable(true);
                    refreshSummary();
                },
                error -> {
                    memberships.clear();
                    refreshSummary();
                    Dialogs.error(I18n.format("student.groupsLoadFailed", FxAsync.messageOf(error)));
                });
    }

    /** حصاد الاشتراكات في سطر: كم مجموعة، وكم منها سارٍ - وهو أول ما يُسأل عنه */
    private void refreshSummary() {
        long active = memberships.stream().filter(MembershipRow::active).count();
        studentSummaryLabel.setText(I18n.format("student.enrollmentsSummary",
                memberships.size(), active, memberships.size() - active));
        printButton.setDisable(memberships.isEmpty());
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
            // بلا مرحلة لا مجموعة تقبله: الاشتراك يُغلق بدل أن يُترك زراً يردّ برسالة خطأ
            subscribeButton.setDisable(true);
            enrollmentHintLabel.setText(I18n.get("student.levelMissingHint"));
            return;
        }

        FxAsync.supply(() -> courseGroupService.getGroupsOfLevel(level),
                groups -> {
                    groupComboBox.getItems().setAll(groups);
                    subscribeButton.setDisable(groups.isEmpty());
                    enrollmentHintLabel.setText(groups.isEmpty()
                            ? I18n.format("student.noGroupsForLevel", level.getDisplayName())
                            : I18n.format("student.groupsOfLevel", level.getDisplayName(), groups.size()));
                },
                error -> Dialogs.error(I18n.format("student.groupsLoadFailed", FxAsync.messageOf(error))));
    }

    @FXML
    public void handleSubscribeAction(ActionEvent event) {
        CourseGroup selectedGroup = groupComboBox.getValue();
        if (selectedGroup == null) {
            Dialogs.warning(I18n.get("student.selectGroup"));
            return;
        }

        // فحص السعة والحفظ داخل Transaction واحدة في EnrollmentService،
        // وعدّ السعة بعد النجاح يجري في الخلفية لا على خيط الواجهة
        FxAsync.supply(() -> {
            enrollmentService.subscribe(student, selectedGroup);
            return enrollmentService.countActiveMembers(selectedGroup);
        }, memberCount -> {
            Dialogs.success(I18n.get("student.subscribed"));
            loadMemberships();
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
        if (row == null || !row.active()) {
            Dialogs.warning(I18n.get("student.selectMembership"));
            return;
        }

        if (!Dialogs.confirm(I18n.get("common.confirm"),
                I18n.format("student.unsubscribeConfirm", row.studentName(), row.groupName()))) {
            return;
        }

        FxAsync.run(() -> enrollmentService.unsubscribe(student.getId(), row.groupId()), () -> {
            Dialogs.success(I18n.get("student.unsubscribed"));
            loadMemberships();
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    /**
     * كشف الاشتراكات: تقرير جاسبر يُبنى في الخلفية ثم يُفتح PDF جاهزاً للطباعة.
     *
     * <p>ملء التقرير وتصديره عمل ثقيل - ترجمة التصميم ورسم الصفحات - فلا يجري على خيط
     * الواجهة، وإلا تجمّدت النافذة حتى تخرج الورقة. والصفوف تُنسخ قبل الانتقال إلى
     * الخلفية: {@code memberships} قائمة الجدول الحيّة، وقراءتها من خيط آخر بينما
     * اشتراكٌ جديد يُضاف إليها من خيط الواجهة تعارضٌ صريح.</p>
     */
    @FXML
    public void handlePrintAction(ActionEvent event) {
        if (memberships.isEmpty()) {
            Dialogs.warning(I18n.get("student.nothingToPrint"));
            return;
        }

        List<MembershipRow> rows = new ArrayList<>(memberships);
        String details = I18n.format("report.enrollments.student",
                student.getBarcode() == null ? I18n.get("common.none") : student.getBarcode(),
                student.getSchoolLevel() == null
                        ? I18n.get("common.none") : student.getSchoolLevel().getDisplayName(),
                student.getPhone() == null ? I18n.get("common.none") : student.getPhone(),
                student.getParentPhone() == null ? I18n.get("common.none") : student.getParentPhone());

        printButton.setDisable(true);

        // القرار هنا لا في الخدمة: "مباشرةً أم PDF" تفضيل جهازٍ يخصّ التسليم وحده،
        // والورقة نفسها تُبنى بالطريقة ذاتها في الحالتين
        if (PrintPreferences.printsSheetsDirectly()) {
            FxAsync.supply(() -> reportService.printStudentEnrollments(student.getName(), details, rows),
                    printer -> {
                        printButton.setDisable(false);
                        Dialogs.success(I18n.format("student.enrollmentsSentTo", printer));
                    },
                    this::printFailed);
        } else {
            FxAsync.supply(() -> reportService.exportStudentEnrollments(student.getName(), details, rows),
                    pdf -> {
                        printButton.setDisable(false);
                        open(pdf);
                    },
                    this::printFailed);
        }
    }

    private void printFailed(Throwable error) {
        printButton.setDisable(false);
        Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error));
    }

    /** فتح الملف بعارض PDF المثبَّت على الجهاز؛ تعذُّر الفتح يُقال ولا يُبتلع */
    private void open(File pdf) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdf);
            } else {
                Dialogs.info(I18n.format("student.enrollmentsPrinted", pdf.getAbsolutePath()));
            }
        } catch (IOException e) {
            Dialogs.error(I18n.format("student.enrollmentsPrinted", pdf.getAbsolutePath()));
        }
    }

    @FXML
    public void handleCloseAction(ActionEvent event) {
        ((Stage) membershipsTable.getScene().getWindow()).close();
    }
}
