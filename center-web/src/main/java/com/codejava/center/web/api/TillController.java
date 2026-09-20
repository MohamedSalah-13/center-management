package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.EnrollmentService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.TransactionService;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.util.MoneyUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * الخزينة: تحصيلٌ من طالب، ومصروفٌ، وحركةُ اليوم، وملخّصُ الوردية.
 *
 * <p>التحصيل يبدأ من الباركود لا من رقم الطالب، لأن ذلك ما يقع عند الشباك: الطالب
 * يقدّم بطاقته. والمجموعةُ تُختار من اشتراكاته القائمة وحدها، فلا يُحصَّل اشتراكٌ
 * لمجموعةٍ ليس فيها.</p>
 */
@RestController
@RequestMapping("/api/till")
@RequiredArgsConstructor
public class TillController {

    private final StudentService studentService;
    private final TransactionService transactionService;
    private final EnrollmentService enrollmentService;

    public record PaymentRequest(@NotBlank String barcode, Long groupId,
                                 @NotNull BigDecimal amount, String description) {
    }

    public record ExpenseRequest(@NotNull BigDecimal amount, String description) {
    }

    public record MovementView(Long id, String type, String typeName, BigDecimal amount,
                               String formatted, String description, LocalDateTime at) {
    }

    public record PaymentView(Long transactionId, String studentName, BigDecimal balance,
                              String formattedBalance) {
    }

    public record EnrolmentView(Long groupId, String groupName) {
    }

    public record SummaryView(LocalDate day, BigDecimal income, BigDecimal expense,
                              BigDecimal payouts, BigDecimal net,
                              String formattedIncome, String formattedExpense,
                              String formattedPayouts, String formattedNet) {
    }

    /** اشتراكات الطالب القائمة: ما يُعرض في قائمة الاختيار قبل التحصيل */
    @GetMapping("/enrolments")
    public List<EnrolmentView> enrolments(@RequestParam String barcode) {
        Student student = studentService.findByBarcode(barcode);
        return enrollmentService.getActiveGroupsOf(student).stream()
                .map(StudentGroup::getGroup)
                .map(group -> new EnrolmentView(group.getId(), group.getName()))
                .toList();
    }

    @PostMapping("/payments")
    public PaymentView pay(@RequestBody PaymentRequest request) {
        Student student = studentService.findByBarcode(request.barcode());
        CourseGroup group = groupOf(student, request.groupId());

        Transaction saved = transactionService.recordStudentPayment(
                student, group, request.amount(), request.description());

        BigDecimal balance = transactionService.getStudentBalance(student.getId());
        return new PaymentView(saved.getId(), student.getName(), balance,
                MoneyUtils.formatWithCurrency(balance));
    }

    @PostMapping("/expenses")
    public MovementView spend(@RequestBody ExpenseRequest request) {
        return movement(transactionService.recordExpense(request.amount(), request.description()));
    }

    @GetMapping("/day")
    public List<MovementView> day(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return transactionService.getCashMovements(date == null ? LocalDate.now() : date).stream()
                .map(TillController::movement)
                .toList();
    }

    @GetMapping("/summary")
    public SummaryView summary(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        ShiftSummary summary = transactionService.getShiftSummary(day);
        return new SummaryView(day, summary.totalIncome(), summary.totalExpense(),
                summary.totalTeacherPayouts(), summary.net(),
                MoneyUtils.formatWithCurrency(summary.totalIncome()),
                MoneyUtils.formatWithCurrency(summary.totalExpense()),
                MoneyUtils.formatWithCurrency(summary.totalTeacherPayouts()),
                MoneyUtils.formatWithCurrency(summary.net()));
    }

    /**
     * المجموعة من اشتراكات الطالب لا من الرقم كما وصل.
     *
     * <p>الرقم يصل من متصفّح، ورقمٌ يكتبه من يريد يربط تحصيلاً بمجموعةٍ لا علاقة
     * للطالب بها - فيقرؤه كشفُ إيراد المجموعة ودفترُ المعلم بعد ذلك على أنه صحيح.</p>
     */
    private CourseGroup groupOf(Student student, Long groupId) {
        if (groupId == null) {
            return null;
        }
        return enrollmentService.getActiveGroupsOf(student).stream()
                .map(StudentGroup::getGroup)
                .filter(group -> group.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        com.codejava.center.util.I18n.get("error.enrollment.notMember")));
    }

    private static MovementView movement(Transaction transaction) {
        return new MovementView(transaction.getId(), transaction.getType().name(),
                transaction.getType().getDisplayName(), transaction.getAmount(),
                MoneyUtils.formatWithCurrency(transaction.getAmount()),
                transaction.getDescription(), transaction.getTransactionDate());
    }
}
