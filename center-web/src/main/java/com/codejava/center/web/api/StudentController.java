package com.codejava.center.web.api;

import com.codejava.center.domain.Student;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * الطلاب: بحثٌ، ورصيد، وكشفُ المتأخرات.
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
