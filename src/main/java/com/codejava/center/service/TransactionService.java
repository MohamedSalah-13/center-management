package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * تسجيل دفع اشتراك طالب
     */
    @Transactional
    public Transaction recordStudentPayment(Student student, CourseGroup group, Double amount, String description) {
        validateAmount(amount);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.INCOME)
                .amount(amount)
                .description(description)
                .transactionDate(LocalDateTime.now())
                .student(student)
                .group(group)
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * تسجيل مصروفات عامة للسنتر (نثريات)
     */
    @Transactional
    public Transaction recordExpense(Double amount, String description) {
        validateAmount(amount);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.EXPENSE)
                .amount(amount)
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
    public Double calculateTodayNetBalance() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        Double totalIncome = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.INCOME, startOfDay, endOfDay);
        Double totalExpense = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.EXPENSE, startOfDay, endOfDay);
        Double totalTeacherPayouts = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.TEACHER_PAYOUT, startOfDay, endOfDay);

        // الصافي = الوارد - (المصروفات + مستحقات المعلمين المدفوعة)
        return totalIncome - (totalExpense + totalTeacherPayouts);
    }

    // دالة مساعدة لمنع إدخال قيم سالبة أو صفرية
    private void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("المبلغ يجب أن يكون أكبر من صفر.");
        }
    }

    /**
     * تسجيل صرف مستحقات المعلمين
     */
    @Transactional
    public void recordTeacherPayout(Double payoutAmount, String description) {
        // 1. التحقق من صحة المبلغ (يجب أن يكون أكبر من صفر)
        validateAmount(payoutAmount);

        // 2. إنشاء كائن الحركة المالية
        Transaction transaction = Transaction.builder()
                .type(TransactionType.TEACHER_PAYOUT) // تحديد نوع الحركة كـ صرف مستحقات
                .amount(payoutAmount)
                .description(description)
                .transactionDate(LocalDateTime.now()) // تسجيل وقت الصرف اللحظي
                .build();

        // 3. حفظ الحركة في قاعدة البيانات
        transactionRepository.save(transaction);
    }
}