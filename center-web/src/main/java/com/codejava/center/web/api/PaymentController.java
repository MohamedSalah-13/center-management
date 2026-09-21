package com.codejava.center.web.api;

import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * سجلّ حركات طالب: مَوردٌ تحت صاحبه، كالاشتراكات.
 *
 * <p>وهو ليس رصيده. الرصيد رقمٌ واحد يجيب "كم عليه الآن"، وهذا يجيب "من أين جاء ذلك
 * الرقم": كلُّ دفعةٍ دفعها وكلُّ رسمِ حصةٍ خُصم منه، من الأحدث. وهو السؤال الذي يقفُ
 * عنده وليُّ الأمر عند المكتب معترضاً على رقم.</p>
 *
 * <p>ولذلك يحمل الصفُّ <b>نوعَه</b>: قائمةٌ لا يُعرف فيها الداخلُ من الخارج تُقرأ على
 * أنها مدفوعاتٌ كلُّها، فتصير خصوم الحصص دفعاتٍ في عين القارئ. و{@code paid} إلى جانب
 * {@code balance} للسبب نفسه: مجموعُ ما دفع لا يساوي رصيده، والخلط بينهما هو الشكوى.</p>
 *
 * <p>وقراءةٌ بلا حارس، كبقية القراءات: هي عن طالبٍ واحد ولا تقول عن السنتر شيئاً -
 * بخلاف {@code getExpenses} و{@code getShiftSummary} وهما عن الخزينة كلها.</p>
 */
@RestController
@RequestMapping("/api/students/{studentId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final TransactionService transactionService;

    public record MovementView(Long id, String type, String typeName, LocalDateTime at,
                               BigDecimal amount, String formatted,
                               String groupName, LocalDate sessionDate, String description) {
    }

    /**
     * @param paid    مجموع ما دفعه - {@code INCOME} وحده
     * @param charged مجموع ما خُصم عليه من رسوم الحصص
     * @param balance رصيده الحالي، وهو <b>ليس</b> الفرق بين الاثنين بالضرورة:
     *                {@code ledgerStartDate} يستبعد ما قبله من الحساب وحده
     */
    public record HistoryView(List<MovementView> rows,
                              BigDecimal paid, String formattedPaid,
                              BigDecimal charged, String formattedCharged,
                              BigDecimal balance, String formattedBalance) {
    }

    @GetMapping
    public HistoryView history(@PathVariable Long studentId) {
        List<Transaction> rows = transactionService.getStudentTransactions(studentId);
        BigDecimal paid = sumOf(rows, TransactionType.INCOME);
        BigDecimal charged = sumOf(rows, TransactionType.SESSION_CHARGE);
        BigDecimal balance = transactionService.getStudentBalance(studentId);

        return new HistoryView(rows.stream().map(PaymentController::view).toList(),
                paid, MoneyUtils.formatWithCurrency(paid),
                charged, MoneyUtils.formatWithCurrency(charged),
                balance, MoneyUtils.formatWithCurrency(balance));
    }

    private static BigDecimal sumOf(List<Transaction> rows, TransactionType type) {
        return MoneyUtils.normalize(rows.stream()
                .filter(row -> row.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * المجموعة والحصة محمَّلتان بـ {@code JOIN FETCH} في الاستعلام، فلا وكيلَ كسولاً
     * يُفتح هنا - و{@code open-in-view} مطفأ، فلو كانتا كسولتين لسقط الطلب.
     */
    private static MovementView view(Transaction row) {
        return new MovementView(row.getId(),
                row.getType() == null ? null : row.getType().name(),
                row.getType() == null ? null : row.getType().getDisplayName(),
                row.getTransactionDate(), row.getAmount(),
                MoneyUtils.formatWithCurrency(row.getAmount()),
                row.getGroup() == null ? null : row.getGroup().getName(),
                row.getSession() == null ? null : row.getSession().getSessionDate(),
                row.getDescription());
    }
}
