package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codejava.center.service.dto.GroupRevenue;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * تسجيل دفع اشتراك طالب مخصص لحصة معينة (لتجنب الدفع المزدوج للحصة)
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public Transaction recordStudentPayment(Student student, CourseGroup group, Session session, BigDecimal amount, String description) {
        validateAmount(amount);

        // التحقق من عدم تكرار الدفع لنفس الحصة (إذا تم تمرير حصة)
        if (session != null) {
            boolean alreadyPaid = transactionRepository.existsByStudentAndSessionAndType(student, session, TransactionType.INCOME);
            if (alreadyPaid) {
                throw new IllegalStateException(I18n.get("error.transaction.sessionAlreadyPaid"));
            }
        }

        Transaction transaction = Transaction.builder()
                .type(TransactionType.INCOME)
                .amount(MoneyUtils.normalize(amount))
                .description(description)
                .transactionDate(LocalDateTime.now())
                .student(student)
                .group(group)
                .session(session) // ربط الحصة بالحركة المالية
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // اسم الطالب يُنسخ إلى السجل: حذف الطالب لاحقاً لا يجوز أن يترك تحصيلاً بلا صاحب
        auditService.record(AuditAction.PAYMENT_RECORDED, saved.getId(),
                student.getName(), saved.getAmount(), description);

        // تنبيه "تأكيد استلام دفعة" يُبنى على هذا الحدث، ولا يُستهلك إلا بعد الإيداع:
        // رسالةٌ تصل ولي الأمر عن دفعة تراجعت معاملتها بعدها لا يمكن سحبها
        eventPublisher.publishEvent(new StudentPaymentRecordedEvent(
                student.getId(), student.getName(), student.getParentPhone(), saved.getAmount()));

        return saved;
    }

    /**
     * الدالة القديمة لضمان عدم تعطل الأكواد والشاشات الأخرى التي لا تمرر "حصة"
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public Transaction recordStudentPayment(Student student, CourseGroup group, BigDecimal amount, String description) {
        return recordStudentPayment(student, group, null, amount, description);
    }

    /**
     * تسجيل مصروفات عامة للسنتر (نثريات)
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public Transaction recordExpense(BigDecimal amount, String description) {
        validateAmount(amount);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.EXPENSE)
                .amount(MoneyUtils.normalize(amount))
                .description(description)
                .transactionDate(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        auditService.record(AuditAction.EXPENSE_RECORDED, saved.getId(),
                null, saved.getAmount(), description);

        return saved;
    }

    /**
     * جرد الخزينة لليوم الحالي (Shift Closing)
     * يعيد صافي الدرج (الوردية) الموجود حالياً
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public BigDecimal calculateTodayNetBalance() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        BigDecimal totalIncome = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.INCOME, startOfDay, endOfDay);
        BigDecimal totalExpense = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.EXPENSE, startOfDay, endOfDay);
        BigDecimal totalTeacherPayouts = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.TEACHER_PAYOUT, startOfDay, endOfDay);

        // الصافي = الوارد - (المصروفات + مستحقات المعلمين المدفوعة)
        return MoneyUtils.normalize(
                MoneyUtils.normalize(totalIncome)
                        .subtract(MoneyUtils.normalize(totalExpense).add(MoneyUtils.normalize(totalTeacherPayouts)))
        );
    }

    // دالة مساعدة لمنع إدخال قيم سالبة أو صفرية
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(I18n.get("error.transaction.amountPositive"));
        }
    }

    /**
     * تسجيل صرف مستحقات المعلمين
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void recordTeacherPayout(BigDecimal payoutAmount, String description) {
        // 1. التحقق من صحة المبلغ (يجب أن يكون أكبر من صفر)
        validateAmount(payoutAmount);

        // 2. إنشاء كائن الحركة المالية
        Transaction transaction = Transaction.builder()
                .type(TransactionType.TEACHER_PAYOUT) // تحديد نوع الحركة كـ صرف مستحقات
                .amount(MoneyUtils.normalize(payoutAmount))
                .description(description)
                .transactionDate(LocalDateTime.now()) // تسجيل وقت الصرف اللحظي
                .build();

        // 3. حفظ الحركة في قاعدة البيانات
        Transaction saved = transactionRepository.save(transaction);
        auditService.record(AuditAction.TEACHER_PAYOUT_PAID, saved.getId(),
                null, saved.getAmount(), description);
    }

    /**
     * جرد الوردية ليوم محدد: تفصيل الوارد والمنصرف والمستحقات المصروفة والصافي.
     * يبني على calculateTodayNetBalance لكنه يُظهر المكوّنات بدل رقم واحد،
     * لأن تقفيل الدرج يحتاج معرفة من أين جاء الفرق.
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public ShiftSummary getShiftSummary(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        // بداية اليوم التالي مع مقارنة "أصغر من": تشمل آخر ثانية بكل أجزائها
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        BigDecimal income = MoneyUtils.normalize(
                transactionRepository.sumAmountByTypeAndDateRange(TransactionType.INCOME, start, end));
        BigDecimal expense = MoneyUtils.normalize(
                transactionRepository.sumAmountByTypeAndDateRange(TransactionType.EXPENSE, start, end));
        BigDecimal payouts = MoneyUtils.normalize(
                transactionRepository.sumAmountByTypeAndDateRange(TransactionType.TEACHER_PAYOUT, start, end));

        BigDecimal net = MoneyUtils.normalize(income.subtract(expense.add(payouts)));
        return new ShiftSummary(income, expense, payouts, net);
    }

    /**
     * مصروفات فترة من تاريخ إلى تاريخ، اليومان طرفاه داخلان فيها.
     *
     * <p>النهاية بداية اليوم التالي مع مقارنة "أصغر من"، كما في {@link #getShiftSummary}:
     * مصروف سُجّل الساعة الحادية عشرة والنصف مساءً في آخر يوم من الشهر يسقط من التقرير
     * لو كانت النهاية {@code atStartOfDay} أو {@code 23:59:59} - وهو نقص لا يظهر إلا
     * حين يُقارَن إجمالي التقرير بدفتر الخزينة فلا يتطابقان بلا سبب ظاهر.</p>
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<Transaction> getExpenses(LocalDate from, LocalDate to) {
        return transactionRepository.findExpenses(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /** الحركات النقدية ليوم محدد (بدون رسوم الحصص) */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<Transaction> getCashMovements(LocalDate day) {
        return transactionRepository.findCashMovements(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /**
     * رصيد الطالب الحالي: ما دفعه ناقص رسوم الحصص التي حضرها.
     * قيمة سالبة تعني متأخرات عليه.
     */
    @Transactional(readOnly = true)
    public BigDecimal getStudentBalance(Long studentId) {
        LocalDateTime ledgerStart = java.util.Optional.ofNullable(settingsService.getSettings().getLedgerStartDate())
                .map(LocalDate::atStartOfDay)
                .orElse(null);

        return MoneyUtils.normalize(transactionRepository.calculateStudentBalance(studentId, ledgerStart));
    }

    /**
     * خصم رسوم حصة من رصيد الطالب عند تسجيل حضوره.
     * ليست حركة نقدية: لا تدخل في جرد الخزينة، بل تمثّل استحقاقاً على الطالب.
     */
    @Transactional
    public Transaction chargeSession(Student student, CourseGroup group, Session session, BigDecimal amount) {
        validateAmount(amount);

        // الحارس الحقيقي: لا تُخصم رسوم نفس الحصة مرتين لنفس الطالب
        if (transactionRepository.existsByStudentAndSessionAndType(student, session, TransactionType.SESSION_CHARGE)) {
            throw new IllegalStateException(I18n.get("error.transaction.sessionAlreadyCharged"));
        }

        Transaction charge = Transaction.builder()
                .type(TransactionType.SESSION_CHARGE)
                .amount(MoneyUtils.normalize(amount))
                .description(I18n.format("transaction.sessionChargeDescription",
                        group.getName(), session.getSessionDate()))
                .transactionDate(LocalDateTime.now())
                .student(student)
                .group(group)
                .session(session)
                .build();

        return transactionRepository.save(charge);
    }

    /**
     * إجمالي الإيرادات مجمّعة حسب المجموعة خلال آخر 30 يوماً (لمخطط لوحة القيادة)
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<GroupRevenue> getRevenueByGroupLast30Days() {
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        return transactionRepository.sumIncomeByGroup(start, end);
    }

    /**
     * جلب سجل المدفوعات لطالب معين مرتبة من الأحدث للأقدم
     */
    @Transactional(readOnly = true)
    public List<Transaction> getStudentTransactions(Long studentId) {
        return transactionRepository.findByStudentIdOrderByTransactionDateDesc(studentId);
    }
}