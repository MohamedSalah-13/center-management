package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.GroupSchedule;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TeacherService;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.Forms;
import com.codejava.center.util.Sheets;
import com.codejava.center.util.WeekDays;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // نسخة جديدة لكل فتح للشاشة - يمنع تراكم الـ listeners والحالة القديمة
@RequiredArgsConstructor
public class GroupManagementController {

    private final CourseGroupService courseGroupService;
    private final TeacherService teacherService;
    private final EnrollmentService enrollmentService;
    private final ReportService reportService;

    @FXML private TextField groupNameField;
    @FXML private CheckBox customNameCheck;
    @FXML private ComboBox<Teacher> teacherComboBox;
    @FXML private ComboBox<SchoolLevel> levelComboBox;
    @FXML private TextField capacityField;
    @FXML private TextField priceField;

    @FXML private CheckBox daySaturday, daySunday, dayMonday, dayTuesday, dayWednesday, dayThursday, dayFriday;
    @FXML private Spinner<Integer> startHourSpinner, startMinuteSpinner, endHourSpinner, endMinuteSpinner;
    @FXML private Label timePreviewLabel;

    @FXML private TableView<CourseGroup> groupsTable;
    @FXML private TableColumn<CourseGroup, String> colId;
    @FXML private TableColumn<CourseGroup, String> colName;
    @FXML private TableColumn<CourseGroup, String> colTeacher;
    @FXML private TableColumn<CourseGroup, String> colLevel;
    @FXML private TableColumn<CourseGroup, String> colDays;
    @FXML private TableColumn<CourseGroup, String> colTime;
    @FXML private TableColumn<CourseGroup, String> colMembers;
    @FXML private TableColumn<CourseGroup, String> colPrice;
    @FXML private TableColumn<CourseGroup, Void> colPrint;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<Teacher> teacherFilterCombo;
    @FXML private ComboBox<DayOfWeek> dayFilterCombo;
    @FXML private ComboBox<LocalTime> timeFilterCombo;

    /** ساعة البدء الافتراضية: أول موعد بعد اليوم الدراسي */
    private static final LocalTime DEFAULT_START = LocalTime.of(16, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    // متغير للاحتفاظ بالمجموعة المحددة حالياً
    private CourseGroup selectedGroup = null;

    private final ObservableList<CourseGroup> groupsList = FXCollections.observableArrayList();
    private final Map<Long, Long> memberCounts = new LinkedHashMap<>();
    private FilteredList<CourseGroup> filteredGroups;

    /** خانة اليوم لكل يوم من أيام الأسبوع - تُبنى مرة واحدة وتُقرأ في الحفظ والعرض */
    private Map<DayOfWeek, CheckBox> dayBoxes;

    @FXML
    public void initialize() {
        dayBoxes = new LinkedHashMap<>();
        dayBoxes.put(DayOfWeek.SATURDAY, daySaturday);
        dayBoxes.put(DayOfWeek.SUNDAY, daySunday);
        dayBoxes.put(DayOfWeek.MONDAY, dayMonday);
        dayBoxes.put(DayOfWeek.TUESDAY, dayTuesday);
        dayBoxes.put(DayOfWeek.WEDNESDAY, dayWednesday);
        dayBoxes.put(DayOfWeek.THURSDAY, dayThursday);
        dayBoxes.put(DayOfWeek.FRIDAY, dayFriday);
        dayBoxes.forEach((day, box) -> {
            box.setText(WeekDays.displayName(day));
            box.selectedProperty().addListener((obs, was, is) -> refreshComposedName());
        });

        setupTableColumns();
        setupTeacherComboBox();
        setupLevelComboBox();
        setupTimeSpinners();
        setupNameMode();
        setupFilters();
        setupTableSelectionListener();
        loadData();

        // تأمين خانة السعة (أرقام صحيحة فقط)
        Forms.numericOnly(capacityField);

        // تأمين خانة السعر (أرقام وكسور عشرية للمبالغ)
        Forms.decimalOnly(priceField);

        // العملة في نصّ الحقل تُضبط هنا لا في FXML: promptText="%key" لا يقبل وسائط،
        // وسنترٌ بدّل عملته كان سيقرأ "القيمة بالجنيه" فوق حقل يُحفظ بالريال
        priceField.setPromptText(I18n.format("group.pricePrompt", MoneyUtils.currencySymbol()));
        Forms.focusNextOnEnter(capacityField, priceField);
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colTeacher.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTeacher().getName()));
        colLevel.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSchoolLevel() == null
                        ? I18n.get("common.none")
                        : data.getValue().getSchoolLevel().getDisplayName()));
        colDays.setCellValueFactory(data -> new SimpleStringProperty(
                WeekDays.describe(data.getValue().getMeetingDays())));
        colTime.setCellValueFactory(data -> new SimpleStringProperty(
                WeekDays.describeRange(data.getValue().getStartTime(), data.getValue().getEndTime())));
        colMembers.setCellValueFactory(data -> new SimpleStringProperty(I18n.format("group.membersOf",
                memberCounts.getOrDefault(data.getValue().getId(), 0L),
                data.getValue().getMaxCapacity() == null
                        ? I18n.get("common.none") : data.getValue().getMaxCapacity())));
        colPrice.setCellValueFactory(data -> new SimpleStringProperty(MoneyUtils.format(data.getValue().getSessionPrice())));
        setupPrintColumn();
    }

    /**
     * زر طباعة داخل كل صف.
     *
     * <p>الزر في الصف لا في شريط الأدوات: كشف مجموعة يُطلب وأنت تنظر إلى سطرها، وزر
     * واحد يطبع "المحدَّد" يعني تحديداً ثم بحثاً عن الزر ثم تأكداً من أنك حدَّدت الصف
     * الصحيح.</p>
     */
    private void setupPrintColumn() {
        colPrint.setCellFactory(column -> new TableCell<>() {
            private final Button printButton = new Button(I18n.get("group.printList"));

            {
                printButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-padding: 2 10;");
                printButton.setOnAction(event -> printRoster(getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : printButton);
            }
        });
    }

    private void setupTeacherComboBox() {
        // جعل الـ ComboBox يعرض اسم المعلم بدلاً من الكائن
        teacherComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Teacher teacher) {
                return teacher == null ? "" : I18n.format("group.teacherWithSubject",
                        teacher.getName(), teacher.getSubject());
            }
            @Override
            public Teacher fromString(String string) { return null; }
        });
        teacherComboBox.valueProperty().addListener((obs, was, is) -> refreshComposedName());
    }

    private void setupLevelComboBox() {
        levelComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SchoolLevel level) {
                return level == null ? "" : level.getDisplayName();
            }
            @Override
            public SchoolLevel fromString(String string) { return null; }
        });
        levelComboBox.getItems().setAll(SchoolLevel.values());
        levelComboBox.valueProperty().addListener((obs, was, is) -> refreshComposedName());
    }

    private void setupTimeSpinners() {
        configureSpinner(startHourSpinner, 0, 23, DEFAULT_START.getHour());
        configureSpinner(startMinuteSpinner, 0, 59, DEFAULT_START.getMinute());
        configureSpinner(endHourSpinner, 0, 23, DEFAULT_END.getHour());
        configureSpinner(endMinuteSpinner, 0, 59, DEFAULT_END.getMinute());
        refreshTimePreview();
    }

    /**
     * يضبط مجال الـ Spinner ويربط مُحرِّره بقيمته.
     *
     * <p>الربط ضروري لا تجميلي: بدونه لا تصل الكتابة اليدوية في الحقل إلى قيمة الـ Spinner
     * إلا بعد ضغط سهم، فيُحفظ موعد غير الذي يراه المستخدم أمامه.</p>
     */
    private void configureSpinner(Spinner<Integer> spinner, int min, int max, int initial) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial);
        factory.setWrapAround(true);
        spinner.setValueFactory(factory);

        spinner.getEditor().textProperty().addListener((obs, was, is) -> {
            try {
                int typed = Integer.parseInt(is.trim());
                if (typed >= min && typed <= max) {
                    factory.setValue(typed);
                }
            } catch (NumberFormatException ignored) {
                // نص غير رقمي أثناء الكتابة؛ القيمة تبقى على آخر رقم صحيح
            }
        });
        spinner.valueProperty().addListener((obs, was, is) -> {
            refreshTimePreview();
            refreshComposedName();
        });
    }

    private void setupNameMode() {
        // الاسم مشتق افتراضياً: الحقل معطَّل ويُملأ من بقية البيانات
        groupNameField.setDisable(true);
        customNameCheck.selectedProperty().addListener((obs, was, isCustom) -> {
            groupNameField.setDisable(!isCustom);
            if (!isCustom) {
                refreshComposedName();
            }
        });
    }

    private void setupFilters() {
        teacherFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Teacher teacher) {
                return teacher == null ? I18n.get("common.all") : teacher.getName();
            }
            @Override
            public Teacher fromString(String string) { return null; }
        });
        dayFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(DayOfWeek day) {
                return day == null ? I18n.get("common.all") : WeekDays.displayName(day);
            }
            @Override
            public DayOfWeek fromString(String string) { return null; }
        });
        timeFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalTime time) {
                return time == null ? I18n.get("common.all") : WeekDays.describeTime(time);
            }
            @Override
            public LocalTime fromString(String string) { return null; }
        });

        // القيمة الفارغة تعني "الكل"، وهي أول الخيارات لا غيابها من القائمة
        List<DayOfWeek> days = new ArrayList<>();
        days.add(null);
        days.addAll(WeekDays.ORDER);
        dayFilterCombo.getItems().setAll(days);

        dayFilterCombo.setValue(null);
        teacherFilterCombo.setValue(null);
        timeFilterCombo.setValue(null);

        filteredGroups = new FilteredList<>(groupsList, group -> true);
        searchField.textProperty().addListener((obs, was, is) -> applyFilters());
        teacherFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());
        dayFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());
        timeFilterCombo.valueProperty().addListener((obs, was, is) -> applyFilters());

        SortedList<CourseGroup> sortedData = new SortedList<>(filteredGroups);
        sortedData.comparatorProperty().bind(groupsTable.comparatorProperty());
        groupsTable.setItems(sortedData);
    }

    private void applyFilters() {
        String text = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        Teacher teacher = teacherFilterCombo.getValue();
        DayOfWeek day = dayFilterCombo.getValue();
        LocalTime time = timeFilterCombo.getValue();

        filteredGroups.setPredicate(group -> {
            if (!text.isEmpty()
                    && !group.getName().toLowerCase().contains(text)
                    && !group.getTeacher().getName().toLowerCase().contains(text)) {
                return false;
            }
            if (teacher != null && !teacher.equals(group.getTeacher())) {
                return false;
            }
            if (day != null && (group.getMeetingDays() == null || !group.getMeetingDays().contains(day))) {
                return false;
            }
            return time == null || time.equals(group.getStartTime());
        });
    }

    private void loadData() {
        // جلب البيانات في Thread منفصل لعدم تجميد الواجهة
        FxAsync.supply(() -> new LoadedData(teacherService.getAllTeachers(),
                        courseGroupService.getAllGroups(),
                        enrollmentService.countActiveMembersPerGroup()),
                data -> {
                    teacherComboBox.getItems().setAll(data.teachers());

                    List<Teacher> filterItems = new ArrayList<>();
                    filterItems.add(null);
                    filterItems.addAll(data.teachers());
                    teacherFilterCombo.getItems().setAll(filterItems);
                    teacherFilterCombo.setValue(null);

                    memberCounts.clear();
                    memberCounts.putAll(data.memberCounts());
                    groupsList.setAll(data.groups());
                    refreshTimeFilterItems();
                },
                error -> Dialogs.error(I18n.format("common.loadFailed", FxAsync.messageOf(error))));
    }

    /** مواعيد البدء المتاحة تُبنى من المجموعات نفسها: قائمة ساعات ثابتة تعرض مواعيد لا وجود لها */
    private void refreshTimeFilterItems() {
        LocalTime chosen = timeFilterCombo.getValue();

        List<LocalTime> times = new ArrayList<>();
        times.add(null);
        groupsList.stream()
                .map(CourseGroup::getStartTime)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(times::add);

        timeFilterCombo.getItems().setAll(times);
        timeFilterCombo.setValue(times.contains(chosen) ? chosen : null);
    }

    private record LoadedData(java.util.List<Teacher> teachers, java.util.List<CourseGroup> groups,
                              Map<Long, Long> memberCounts) {
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        CourseGroup newGroup = readForm(new CourseGroup());
        if (newGroup == null) {
            return;
        }

        FxAsync.supply(() -> courseGroupService.saveGroup(newGroup), savedGroup -> {
            groupsList.add(savedGroup); // إضافة فورية للجدول
            refreshTimeFilterItems();
            clearForm();
            Dialogs.success(I18n.get("group.added"));
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    /**
     * قراءة النموذج في كيان، أو {@code null} مع رسالة إن كان ناقصاً.
     * ما يمكن فحصه هنا يُفحص هنا، والقيود الحقيقية (التعارض، الصف) في الخدمة.
     */
    private CourseGroup readForm(CourseGroup target) {
        Teacher teacher = teacherComboBox.getValue();
        SchoolLevel level = levelComboBox.getValue();
        String capacityStr = capacityField.getText().trim();
        String priceStr = priceField.getText().trim();
        Set<DayOfWeek> days = selectedDays();

        if (teacher == null || level == null || capacityStr.isEmpty() || priceStr.isEmpty() || days.isEmpty()) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("group.fillAllFields"));
            return null;
        }
        if (customNameCheck.isSelected() && groupNameField.getText().trim().isEmpty()) {
            Dialogs.warning(I18n.get("common.missingData"), I18n.get("group.fillAllFields"));
            return null;
        }

        try {
            target.setTeacher(teacher);
            target.setSchoolLevel(level);
            target.setMaxCapacity(Integer.parseInt(capacityStr));
            target.setSessionPrice(new BigDecimal(priceStr));
            target.setMeetingDays(days);
            target.setStartTime(readTime(startHourSpinner, startMinuteSpinner));
            target.setEndTime(readTime(endHourSpinner, endMinuteSpinner));
            target.setAutoName(!customNameCheck.isSelected());
            target.setName(customNameCheck.isSelected()
                    ? groupNameField.getText().trim()
                    : GroupSchedule.compose(target));
            return target;
        } catch (NumberFormatException e) {
            Dialogs.error(I18n.get("common.numberError"), I18n.get("group.numbersInvalid"));
            return null;
        }
    }

    private Set<DayOfWeek> selectedDays() {
        return dayBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private LocalTime readTime(Spinner<Integer> hour, Spinner<Integer> minute) {
        return LocalTime.of(spinnerValue(hour, 0, 23), spinnerValue(minute, 0, 59));
    }

    /**
     * قيمة الـ Spinner مقصورة على مجالها بعد تثبيت ما كُتب في مُحرِّره.
     * القصر ضروري: القيمة الخارجة عن المجال ترمي استثناءً في {@link LocalTime#of}.
     */
    private int spinnerValue(Spinner<Integer> spinner, int min, int max) {
        Integer value = spinner.getValue();
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void refreshTimePreview() {
        if (timePreviewLabel == null) {
            return;
        }
        timePreviewLabel.setText(WeekDays.describeRange(
                readTime(startHourSpinner, startMinuteSpinner),
                readTime(endHourSpinner, endMinuteSpinner)));
    }

    /** معاينة الاسم المشتق أثناء الكتابة: ما يُعرض هو ما سيُحفَظ */
    private void refreshComposedName() {
        if (customNameCheck == null || customNameCheck.isSelected()) {
            return;
        }
        Teacher teacher = teacherComboBox.getValue();
        SchoolLevel level = levelComboBox.getValue();
        Set<DayOfWeek> days = selectedDays();

        // نموذج فارغ يبقى فارغاً: اسم مكوَّن من ثلاث شرطات ليس معاينة لشيء
        if (teacher == null && level == null && days.isEmpty()) {
            groupNameField.clear();
            return;
        }

        groupNameField.setText(GroupSchedule.compose(level,
                teacher == null ? null : teacher.getName(),
                days,
                readTime(startHourSpinner, startMinuteSpinner)));
    }

    /**
     * الاستماع لتحديد صف في الجدول لتعبئة الحقول
     */
    private void setupTableSelectionListener() {
        groupsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedGroup = newSelection;
                customNameCheck.setSelected(!selectedGroup.isAutoName());
                groupNameField.setText(selectedGroup.getName());
                teacherComboBox.setValue(selectedGroup.getTeacher());
                levelComboBox.setValue(selectedGroup.getSchoolLevel());
                capacityField.setText(String.valueOf(selectedGroup.getMaxCapacity()));
                priceField.setText(MoneyUtils.format(selectedGroup.getSessionPrice()));

                Set<DayOfWeek> days = selectedGroup.getMeetingDays();
                dayBoxes.forEach((day, box) -> box.setSelected(days != null && days.contains(day)));
                setTime(startHourSpinner, startMinuteSpinner, selectedGroup.getStartTime(), DEFAULT_START);
                setTime(endHourSpinner, endMinuteSpinner, selectedGroup.getEndTime(), DEFAULT_END);

                // تفعيل أزرار التعديل والحذف
                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    private void setTime(Spinner<Integer> hour, Spinner<Integer> minute, LocalTime value, LocalTime fallback) {
        LocalTime time = value != null ? value : fallback;
        hour.getValueFactory().setValue(time.getHour());
        minute.getValueFactory().setValue(time.getMinute());
    }

    @FXML
    public void handleUpdateAction(ActionEvent event) {
        if (selectedGroup == null) return;

        CourseGroup target = readForm(selectedGroup);
        if (target == null) {
            return;
        }

        FxAsync.supply(() -> courseGroupService.saveGroup(target), updatedGroup -> {
            // تحديث الجدول بصرياً - البحث عن الموضع في القائمة المصدر وليس في العرض
            // (الجدول مربوط بـ SortedList/FilteredList ولذلك يختلف ترتيب صفوفه عن groupsList)
            int selectedIndex = groupsList.indexOf(target);
            if (selectedIndex >= 0) {
                groupsList.set(selectedIndex, updatedGroup);
            }
            refreshTimeFilterItems();
            clearForm();
            Dialogs.success(I18n.get("common.updated"));
        }, error -> Dialogs.error(FxAsync.messageOf(error)));
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedGroup == null) return;

        if (Dialogs.confirm(I18n.get("common.confirmDelete"),
                I18n.format("group.deleteConfirm", selectedGroup.getName()))) {
            CourseGroup target = selectedGroup;
            FxAsync.run(() -> courseGroupService.deleteGroup(target.getId()), () -> {
                groupsList.remove(target); // الإزالة من الجدول
                memberCounts.remove(target.getId());
                refreshTimeFilterItems();
                clearForm();
                Dialogs.success(I18n.get("common.deleted"));
            }, error -> Dialogs.error(I18n.get("group.deleteBlocked")));
        }
    }

    @FXML
    private void clearForm() {
        customNameCheck.setSelected(false);
        groupNameField.clear();
        teacherComboBox.setValue(null);
        levelComboBox.setValue(null);
        capacityField.clear();
        priceField.clear();
        dayBoxes.values().forEach(box -> box.setSelected(false));
        setTime(startHourSpinner, startMinuteSpinner, DEFAULT_START, DEFAULT_START);
        setTime(endHourSpinner, endMinuteSpinner, DEFAULT_END, DEFAULT_END);

        // إعادة ضبط حالة التحديد والأزرار
        selectedGroup = null;
        groupsTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    /**
     * كشف مجموعة واحدة: تقرير جاسبر يُقرأ ويُبنى ويُسلَّم كلُّه في الخلفية.
     *
     * <p>قراءة المشتركين وملء الورقة في نفس الاستدعاء لا في اثنين: الأول وحده كان يعود إلى
     * خيط الواجهة ليُطبع عليه، لأن {@code PrinterJob} في مسار JavaFX لا يعمل خارجه. وجاسبر
     * لا يشترط ذلك، فلا شيء يعود إلى خيط الواجهة إلا نتيجة التسليم.</p>
     */
    private void printRoster(CourseGroup group) {
        FxAsync.supply(() -> reportService.deliverGroupRoster(group,
                        enrollmentService.getRoster(group.getId())),
                Sheets::show,
                error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
    }

    /** كشف بكل المجموعات المعروضة بعد التصفية - كل المجموعات، أو معلم بعينه، أو موعد بعينه */
    @FXML
    public void handlePrintFilteredAction(ActionEvent event) {
        List<CourseGroup> shown = new ArrayList<>(groupsTable.getItems());
        if (shown.isEmpty()) {
            Dialogs.warning(I18n.get("group.nothingToPrint"));
            return;
        }

        try {
            reportService.printGroupsList(shown, Map.copyOf(memberCounts), describeFilters(),
                    groupsTable.getScene().getWindow());
        } catch (Exception e) {
            Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(e));
        }
    }

    /** وصف التصفية كما يُطبع في ترويسة الكشف */
    private String describeFilters() {
        List<String> parts = new ArrayList<>();
        if (teacherFilterCombo.getValue() != null) {
            parts.add(I18n.format("group.filterTeacherAs", teacherFilterCombo.getValue().getName()));
        }
        if (dayFilterCombo.getValue() != null) {
            parts.add(I18n.format("group.filterDayAs", WeekDays.displayName(dayFilterCombo.getValue())));
        }
        if (timeFilterCombo.getValue() != null) {
            parts.add(I18n.format("group.filterTimeAs", WeekDays.describeTime(timeFilterCombo.getValue())));
        }
        if (searchField.getText() != null && !searchField.getText().isBlank()) {
            parts.add(I18n.format("group.filterSearchAs", searchField.getText().trim()));
        }

        return parts.isEmpty()
                ? I18n.get("group.filterAll")
                : String.join(I18n.get("common.listSeparator") + " ", parts);
    }
}
