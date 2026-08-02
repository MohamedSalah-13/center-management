package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
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
import com.codejava.center.util.MoneyUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CenterSettingsRepository centerSettingsRepository;

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
                throw new IllegalStateException("تم دفع مصروفات هذه الحصة مسبقاً لهذا الطالب.");
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

        return transactionRepository.save(transaction);
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

        return transactionRepository.save(transaction);
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
            throw new IllegalArgumentException("المبلغ يجب أن يكون أكبر من صفر.");
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
        transactionRepository.save(transaction);
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
        LocalDateTime ledgerStart = centerSettingsRepository.findById(1L)
                .map(CenterSettings::getLedgerStartDate)
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
            throw new IllegalStateException("تم خصم رسوم هذه الحصة مسبقاً لهذا الطالب.");
        }

        Transaction charge = Transaction.builder()
                .type(TransactionType.SESSION_CHARGE)
                .amount(MoneyUtils.normalize(amount))
                .description("رسوم حصة: " + group.getName() + " بتاريخ " + session.getSessionDate())
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