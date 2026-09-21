package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.service.dto.StudentDraft;
import com.codejava.center.util.MoneyUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * الطلاب: تسجيلٌ وتعديل وأرشفة، وبحثٌ ورصيدٌ وكشفُ متأخرات.
 *
 * <p>الكتابة وصلت مع {@code StudentDraft}: ما دام المدخل كيانَ {@code Student} فلا
 * تسجيلَ من الويب أصلاً - <b>لا كيان JPA مدخلاً لـ HTTP أبداً</b> - فكان نصفُ الشاشة
 * في طبقة الأعمال لا في الحافة، وهو ما سبق هذه الدفعة.</p>
 *
 * <p>لا منطق هنا - الخدمة نفسها التي تستدعيها شاشة سطح المكتب، بالحارس نفسه:
 * {@code getStudentsInArrears} مقصورة على المدير، فتردّ الحافة {@code 403} حيث
 * تُخفي الشاشة زراً.</p>
 *
 * <p>والصفوف تُنسخ إلى سجلّاتٍ صغيرة ولا يُسلَّم الكيان كما هو: {@code Student}
 * يحمل علاقاتٍ كسولة، وتسلسلُها خارج المعاملة يُسقط الطلب - وما ينجح منها يسحب نصف
 * القاعدة إلى جواب واحد.</p>
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final TransactionService transactionService;
    private final EnrollmentService enrollmentService;

    /**
     * جسمُ الطلب، و<b>لا معرّف فيه</b>.
     *
     * <p>المعرّف يأتي من المسار وحده: جسمٌ يحمل معرّفه يجعل الطلب الواحد يقول شيئين
     * عمّن يُعدَّل، والمسار هو ما رآه سجلُّ الخادم وما يقرؤه من يراجع بعد شهر. وطلبٌ
     * إلى {@code /api/students/7} يكتب في الطالب رقم 9 هو ما تُبنى عليه الثغرة.</p>
     */
    public record StudentRequest(
            @Size(max = 50) String barcode,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 15) String phone,
            @Size(max = 15) String parentPhone,
            SchoolLevel schoolLevel) {

        StudentDraft toDraft(Long id) {
            return new StudentDraft(id, barcode, name, phone, parentPhone, schoolLevel);
        }
    }

    /** مجموعةٌ سارية يناقضها الصف الجديد - انظر {@code levelClashes} */
    public record LevelClashView(Long groupId, String groupName, String level) {
    }

    /** الثابتُ يُرسَل والمترجَمُ يُعرض */
    public record LevelView(String name, String label) {
    }

    public record StudentView(Long id, String barcode, String name, String phone,
                              String parentPhone, String level, boolean active) {
    }

    public record BalanceView(Long studentId, BigDecimal balance, String formatted) {
    }

    public record ArrearsView(Long studentId, String name, String barcode, String parentPhone,
                              BigDecimal amountDue, String formatted) {
    }

    /**
     * @param query نصُّ بحثٍ في الاسم أو الباركود؛ فارغٌ يعني الكل
     */
    @GetMapping
    public List<StudentView> list(@RequestParam(required = false) String query,
                                  @RequestParam(defaultValue = "false") boolean includeArchived) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return studentService.getStudents(includeArchived).stream()
                .filter(student -> needle.isEmpty() || matches(student, needle))
                .map(StudentController::view)
                .toList();
    }

    @GetMapping("/{id}/balance")
    public BalanceView balance(@PathVariable Long id) {
        BigDecimal balance = transactionService.getStudentBalance(id);
        return new BalanceView(id, balance, MoneyUtils.formatWithCurrency(balance));
    }

    @GetMapping("/arrears")
    public List<ArrearsView> arrears() {
        return studentService.getStudentsInArrears().stream()
                .map(StudentController::arrears)
                .toList();
    }

    /**
     * الصفوف الدراسية بأسمائها المترجَمة.
     *
     * <p>الصفحة لا تحمل نصاً عربياً - لا في {@code .html} ولا في {@code .js} - فقائمةٌ
     * مكتوبةٌ هناك تتجمّد على لغة من كتبها. والاسم الثابت هو ما يُرسَل، والمترجَم هو
     * ما يُعرض، تماماً كما يفعل {@code getDisplayName} في الشاشات.</p>
     */
    @GetMapping("/levels")
    public List<LevelView> levels() {
        return java.util.Arrays.stream(SchoolLevel.values())
                .map(level -> new LevelView(level.name(), level.getDisplayName()))
                .toList();
    }

    @GetMapping("/{id}")
    public StudentView one(@PathVariable Long id) {
        return view(studentService.findById(id));
    }

    @PostMapping
    public StudentView create(@Valid @RequestBody StudentRequest request) {
        return view(studentService.saveStudent(request.toDraft(null)));
    }

    @PutMapping("/{id}")
    public StudentView update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return view(studentService.saveStudent(request.toDraft(id)));
    }

    /**
     * الأرشفة والإعادة، وهما فعلان لا حقلٌ في نموذج.
     *
     * <p>الحقل غائب عن {@link StudentRequest} عمداً - كما غاب عن {@code StudentDraft} -
     * فتعديلُ رقم هاتفٍ لا يستطيع أن يعيد مؤرشفاً إلى بوابة الحضور. وهذان البابان
     * يكتبان سطرَهما في سجل المراقبة باسمه: {@code STUDENT_ARCHIVED} و
     * {@code STUDENT_RESTORED}.</p>
     */
    @PostMapping("/{id}/archive")
    public StudentView archive(@PathVariable Long id) {
        return view(studentService.setArchived(id, true));
    }

    @PostMapping("/{id}/restore")
    public StudentView restore(@PathVariable Long id) {
        return view(studentService.setArchived(id, false));
    }

    /**
     * الحذف، وهو ليس المخرجَ المعتاد.
     *
     * <p>من له حضورٌ أو حركةٌ مالية لا يُحذف، والخدمة تقول ذلك بجملةٍ مترجمة تصل
     * هنا {@code 409} وتدلّ على الأرشفة. وبغيرها كان قيدُ المفتاح الأجنبي يصل
     * {@code 500} بجملة "حدث خطأ غير متوقع".</p>
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
    }

    /**
     * المجموعات السارية التي يناقضها صفٌّ جديد - تُسأل <b>قبل</b> الحفظ.
     *
     * <p>قيد الصف يُفحص عند الاشتراك وحده، فتغيير مرحلة طالبٍ مشترك يتجاوزه باباً
     * خلفياً. والمنع خطأ - الترقية في أول العام تقع لكل طالب مرة كل سنة - فالمخرج أن
     * يُرى الأثر: الشاشة تعرض المخالف وتطلب تأكيداً، وهو ما تفعله شاشة سطح المكتب.
     * وحافةٌ بلا هذا السؤال تكون قد ألغت القيد بصمت لمن يستعمل الويب.</p>
     */
    @GetMapping("/{id}/level-clashes")
    public List<LevelClashView> levelClashes(@PathVariable Long id,
                                             @RequestParam(required = false) SchoolLevel level) {
        return enrollmentService.findEnrolmentsOutsideLevel(id, level).stream()
                .map(StudentController::clash)
                .toList();
    }

    private static LevelClashView clash(CourseGroup group) {
        return new LevelClashView(group.getId(), group.getName(),
                group.getSchoolLevel() == null ? null : group.getSchoolLevel().getDisplayName());
    }

    private static boolean matches(Student student, String needle) {
        return contains(student.getName(), needle) || contains(student.getBarcode(), needle)
                || contains(student.getPhone(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static StudentView view(Student student) {
        return new StudentView(student.getId(), student.getBarcode(), student.getName(),
                student.getPhone(), student.getParentPhone(),
                student.getSchoolLevel() == null ? null : student.getSchoolLevel().getDisplayName(),
                student.isActive());
    }

    private static ArrearsView arrears(StudentBalance row) {
        return new ArrearsView(row.studentId(), row.studentName(), row.barcode(),
                row.parentPhone(), row.amountDue(),
                MoneyUtils.formatWithCurrency(row.amountDue()));
    }
}
