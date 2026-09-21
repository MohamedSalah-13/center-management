package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.ReportService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TeacherService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.Sheet;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * التقارير كـ PDF.
 *
 * <p>هذا هو الشطر الآخر من الخطّ الذي فصل الملء عن التسليم: {@code ReportService}
 * يملأ ويقف - كلُّ دالة تعيد {@link Sheet} - و{@code util/Sheets} على الجهاز يقرّر
 * أتذهب إلى طابعة أم إلى عارض PDF. وهنا القرارُ ثالث: البايتات تدخل جواب HTTP.</p>
 *
 * <p>ولولا ذلك الفصل لَكان على هذا الملف أن يعيد بناء كل تقرير من أوله - أو أن
 * يستدعي خدمةً تفتح عارض PDF على خادمٍ بلا شاشة.</p>
 *
 * <p>ولا طباعةَ مباشرة هنا ولا عرضَ قبلي: طابعةُ الخادم - إن وُجدت - ليست الطابعة
 * التي أمام من طلب التقرير، و"العرض قبل الطباعة" هو فتحُ الملفّ نفسه في المتصفّح.</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    /** في اسم الملف، لا على الورقة: يُرتَّب في مجلّد التنزيلات فيصطفّ زمنياً */
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.ROOT);

    private final ReportService reportService;
    private final StudentService studentService;
    private final TransactionService transactionService;
    private final AttendanceService attendanceService;
    private final CourseGroupService courseGroupService;
    private final TeacherService teacherService;

    @GetMapping("/arrears.pdf")
    public ResponseEntity<byte[]> arrears() {
        List<StudentBalance> rows = studentService.getStudentsInArrears();
        BigDecimal total = rows.stream()
                .map(StudentBalance::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return pdf(reportService.arrearsSheet(rows, total));
    }

    @GetMapping("/shift.pdf")
    public ResponseEntity<byte[]> shift(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return pdf(reportService.shiftSummarySheet(day,
                transactionService.getShiftSummary(day),
                transactionService.getCashMovements(day)));
    }

    @GetMapping("/attendance-log.pdf")
    public ResponseEntity<byte[]> attendanceLog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long groupId) {

        // وصفُ المرشّح يُطبع على الورقة: صفحةٌ تُوجد على الطابعة بلا مداها تُقرأ
        // على أنها كشف كل شيء - وهو نفس النصّ الذي تبنيه شاشة الجهاز
        String scope = I18n.format("attLog.filterDescription", from, to, groupName(groupId));
        return pdf(reportService.attendanceLogSheet(
                attendanceService.getAttendanceLog(from, to, groupId), scope));
    }

    /**
     * كشف المصروفات، ومعه <b>نفس</b> التصفية التي بَنت الأرقام على الشاشة.
     *
     * <p>البنود والإجمالي ووصفُ المدى كلُّها من {@code ExpenseController}، فالورقة
     * نسخةٌ مما كان أمام من ضغط الزرّ لا ترتيبٌ ثانٍ يقرؤه من جديد. وبغير ذلك تخرج
     * ورقةٌ بإجمالي الفترة كلها لمن كان ينظر إلى نتيجة بحث.</p>
     */
    @GetMapping("/expenses.pdf")
    public ResponseEntity<byte[]> expenses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String query) {

        List<Transaction> rows = ExpenseController.filtered(
                transactionService.getExpenses(from, to), query);
        BigDecimal total = rows.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return pdf(reportService.expenseReportSheet(rows, total,
                ExpenseController.scopeOf(from, to, query)));
    }

    /**
     * كشف حساب معلم: حصصه المغلقة التي لم تُصرَف بعد.
     *
     * <p>هو الورقة التي تُسلَّم مع المال، فلا يقرأ المعلم رقماً واحداً بلا الحصص التي
     * تكوّن منها.</p>
     */
    @GetMapping("/teacher-statement.pdf")
    public ResponseEntity<byte[]> teacherStatement(@RequestParam Long teacherId) {
        List<SessionPayout> sessions = teacherService.getPayableSessionsOf(teacherId);
        return pdf(reportService.teacherStatementSheet(teacherService.findById(teacherId), sessions));
    }

    private String groupName(Long groupId) {
        if (groupId == null) {
            return I18n.get("common.all");
        }
        return courseGroupService.getAllGroups().stream()
                .filter(group -> group.getId().equals(groupId))
                .map(CourseGroup::getName)
                .findFirst()
                .orElseGet(() -> I18n.get("common.all"));
    }

    /**
     * {@code inline} لا {@code attachment}: المتصفّح يفتح الملف في تبويب - وهو نفسه
     * ما يفعله الجهاز حين لا تكون الطباعة المباشرة مفعَّلة - ومن أراد حفظه حفظه.
     *
     * <p>والاسم يُكتب مرتين، {@code filename} و{@code filename*}: الأول للمتصفّحات
     * القديمة والثاني هو الوحيد الذي يحمل حروفاً غير لاتينية سليمة. تبني
     * {@link ContentDisposition} الاثنين من نفس القيمة.</p>
     */
    private ResponseEntity<byte[]> pdf(Sheet sheet) {
        String name = sheet.fileNamePrefix() + LocalDate.now().format(FILE_STAMP) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(name, StandardCharsets.UTF_8).toString())
                .body(sheet.toPdf());
    }
}
