package com.codejava.center.controller;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.TeacherService;
import com.codejava.center.util.InputValidator;
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
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class GroupManagementController {

    private final CourseGroupService courseGroupService;
    private final TeacherService teacherService;
    private final ReportService reportService;

    @FXML private TextField groupNameField;
    @FXML private ComboBox<Teacher> teacherComboBox;
    @FXML private TextField capacityField;
    @FXML private TextField priceField;

    @FXML private TableView<CourseGroup> groupsTable;
    @FXML private TableColumn<CourseGroup, String> colId;
    @FXML private TableColumn<CourseGroup, String> colName;
    @FXML private TableColumn<CourseGroup, String> colTeacher;
    @FXML private TableColumn<CourseGroup, String> colCapacity;
    @FXML private TableColumn<CourseGroup, String> colPrice;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Button printButton;
    @FXML private TextField searchField;

    // متغير للاحتفاظ بالمجموعة المحددة حالياً
    private CourseGroup selectedGroup = null;

    private final ObservableList<CourseGroup> groupsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupTeacherComboBox();
        loadData();
        setupTableSelectionListener();

        // تأمين خانة السعة (أرقام صحيحة فقط)
        InputValidator.makeNumericOnly(capacityField);

        // تأمين خانة السعر (أرقام وكسور عشرية للمبالغ)
        InputValidator.makeDecimalOnly(priceField);

        FilteredList<CourseGroup> filteredData = new FilteredList<>(groupsList, b -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(group -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                if (group.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (group.getTeacher().getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        SortedList<CourseGroup> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(groupsTable.comparatorProperty());
        groupsTable.setItems(sortedData);
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colTeacher.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTeacher().getName()));
        colCapacity.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getMaxCapacity())));
        colPrice.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getSessionPrice())));

        groupsTable.setItems(groupsList);
    }

    private void setupTeacherComboBox() {
        // جعل الـ ComboBox يعرض اسم المعلم بدلاً من الكائن
        teacherComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Teacher teacher) {
                return teacher == null ? "" : teacher.getName() + " (" + teacher.getSubject() + ")";
            }
            @Override
            public Teacher fromString(String string) { return null; }
        });
    }

    private void loadData() {
        // جلب البيانات في Thread منفصل لعدم تجميد الواجهة
        CompletableFuture.runAsync(() -> {
            var teachers = teacherService.getAllTeachers();
            var groups = courseGroupService.getAllGroups();

            Platform.runLater(() -> {
                teacherComboBox.getItems().setAll(teachers);
                groupsList.setAll(groups);
            });
        });
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        String name = groupNameField.getText().trim();
        Teacher selectedTeacher = teacherComboBox.getValue();
        String capacityStr = capacityField.getText().trim();
        String priceStr = priceField.getText().trim();

        if (name.isEmpty() || selectedTeacher == null || capacityStr.isEmpty() || priceStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "بيانات ناقصة", "يرجى تعبئة جميع الحقول.");
            return;
        }

        try {
            CourseGroup newGroup = CourseGroup.builder()
                    .name(name)
                    .teacher(selectedTeacher)
                    .maxCapacity(Integer.parseInt(capacityStr))
                    .sessionPrice(Double.parseDouble(priceStr))
                    .build();

            CourseGroup savedGroup = courseGroupService.saveGroup(newGroup);

            groupsList.add(savedGroup); // إضافة فورية للجدول
            clearForm();
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تمت إضافة المجموعة بنجاح.");

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "خطأ في الأرقام", "السعة والسعر يجب أن تكون أرقاماً صحيحة.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    /**
     * الاستماع لتحديد صف في الجدول لتعبئة الحقول
     */
    private void setupTableSelectionListener() {
        groupsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedGroup = newSelection;
                groupNameField.setText(selectedGroup.getName());
                teacherComboBox.setValue(selectedGroup.getTeacher());
                capacityField.setText(String.valueOf(selectedGroup.getMaxCapacity()));
                priceField.setText(String.valueOf(selectedGroup.getSessionPrice()));

                // تفعيل أزرار التعديل والحذف
                updateButton.setDisable(false);
                deleteButton.setDisable(false);
                printButton.setDisable(false);
            }
        });
    }

    @FXML
    public void handleUpdateAction(ActionEvent event) {
        if (selectedGroup == null) return;

        String name = groupNameField.getText().trim();
        Teacher selectedTeacher = teacherComboBox.getValue();
        String capacityStr = capacityField.getText().trim();
        String priceStr = priceField.getText().trim();

        if (name.isEmpty() || selectedTeacher == null || capacityStr.isEmpty() || priceStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى تعبئة جميع الحقول.");
            return;
        }

        try {
            // تحديث الكيان الحالي بدلاً من إنشاء واحد جديد
            selectedGroup.setName(name);
            selectedGroup.setTeacher(selectedTeacher);
            selectedGroup.setMaxCapacity(Integer.parseInt(capacityStr));
            selectedGroup.setSessionPrice(Double.parseDouble(priceStr));

            CourseGroup updatedGroup = courseGroupService.saveGroup(selectedGroup);

            // تحديث الجدول بصرياً
            int selectedIndex = groupsTable.getSelectionModel().getSelectedIndex();
            groupsList.set(selectedIndex, updatedGroup);

            clearForm();
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم التعديل بنجاح.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    @FXML
    public void handleDeleteAction(ActionEvent event) {
        if (selectedGroup == null) return;

        // نافذة تأكيد قبل الحذف
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هل أنت متأكد من حذف المجموعة: " + selectedGroup.getName() + "؟", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    courseGroupService.deleteGroup(selectedGroup.getId());
                    groupsList.remove(selectedGroup); // الإزالة من الجدول
                    clearForm();
                    showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم الحذف بنجاح.");
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "خطأ", "لا يمكن حذف المجموعة لارتباطها بسجلات أخرى (حصص أو طلاب).");
                }
            }
        });
    }

    @FXML
    private void clearForm() {
        groupNameField.clear();
        teacherComboBox.setValue(null);
        capacityField.clear();
        priceField.clear();

        // إعادة ضبط حالة التحديد والأزرار
        selectedGroup = null;
        groupsTable.getSelectionModel().clearSelection();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
        printButton.setDisable(true);
    }

    public void printGroupReport(Long groupId) {
        // تجهيز المعاملات (Parameters) التي سيستقبلها التقرير
        Map<String, Object> params = new HashMap<>();
        params.put("GROUP_ID", groupId);

        // تحديد مسار الحفظ (مثلاً سطح المكتب)
        String userHome = System.getProperty("user.home");
        String outputPath = userHome + "/Desktop/Group_" + groupId + "_Report.pdf";

        // تنفيذ المهمة في الخلفية
        CompletableFuture.runAsync(() -> {
            try {
                reportService.generatePdfReport("GroupStudents", params, outputPath);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).thenRun(() -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم استخراج التقرير وحفظه على سطح المكتب.");
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                ex.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل استخراج التقرير: " + ex.getCause().getMessage());
            });
            return null;
        });
    }

    public void previewGroupReport(Long groupId) {
        // تجهيز المعاملات (Parameters) التي سيستقبلها التقرير
        Map<String, Object> params = new HashMap<>();
        params.put("GROUP_ID", groupId);

        // تنفيذ المهمة في الخلفية لعدم تجميد واجهة الـ JavaFX
        CompletableFuture.runAsync(() -> {
            try {
                // استدعاء دالة المعاينة بدلاً من التصدير لملف
                reportService.showReportPreview("GroupStudents", params);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل عرض التقرير: " + ex.getCause().getMessage());
            });
            return null;
        });
    }

    @FXML
    public void handlePrintAction(ActionEvent event) {
        // التأكد من أنه تم تحديد مجموعة بالفعل
        if (selectedGroup == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى تحديد مجموعة من الجدول أولاً للعرض.");
            return;
        }

        // استدعاء دالة المعاينة للمجموعة المحددة
        printGroupReport(selectedGroup.getId());
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}