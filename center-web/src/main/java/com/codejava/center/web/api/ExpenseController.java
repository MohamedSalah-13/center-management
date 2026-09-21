package com.codejava.center.web.api;

import com.codejava.center.domain.Transaction;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * المصروفات على مدى فترة - سؤالٌ غير سؤال شاشة الخزينة.
 *
 * <p>{@code /api/till/expenses} <b>يسجّل</b> مصروفاً و{@code /api/till/day} يعرض يوماً
 * واحداً؛ وهذا يقرأ ما سُجِّل على مدى شهرٍ أو مدّةٍ يختارها من يجمع المصروفات مقابل
 * إيراد الشهر. نفس القسمة التي بين {@code Expenses.fxml} و{@code ExpenseReport.fxml}:
 * واحدةٌ لمن بيده الخزينة الآن، والأخرى لمن يراجع الشهر.</p>
 *
 * <p>والتصفية بالنصّ تقع هنا لا في المتصفّح، <b>لأن الإجمالي والورقة يجب أن يخرجا من
 * القائمة نفسها</b>: من يبحث عن "كهرباء" ثم يقرأ إجمالياً يشمل كل المصروفات ينسب
 * مصروفات الشهر كلها إلى فاتورة الكهرباء. الوصف المبنيُّ هنا هو الذي يُطبع في ترويسة
 * الورقة، فلا يقرأ أحدٌ صفحةً تبدو كشفَ الفترة كلها وهي نتيجةُ بحث.</p>
 *
 * <p>والقراءة محروسة {@code @RequiresRole(ADMIN)} في {@code TransactionService}، كما
 * تُخفي الشاشةُ زرَّ المصروفات عن السكرتير.</p>
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final TransactionService transactionService;

    public record ExpenseView(Long id, LocalDateTime at, String description,
                              BigDecimal amount, String formatted) {
    }

    /**
     * @param rows     البنود بعد التصفية
     * @param total    إجماليها هي، لا إجمالي الفترة
     * @param largest  أكبر بند فيها - وهو ما يجعل الإجمالي قابلاً للقراءة
     * @param scope    وصف المدى والبحث، بالنصّ الذي يُطبع على الورقة
     */
    public record ExpenseReportView(List<ExpenseView> rows, BigDecimal total, String formattedTotal,
                                    BigDecimal largest, String formattedLargest, String scope) {
    }

    @GetMapping
    public ExpenseReportView expenses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String query) {

        List<Transaction> rows = filtered(transactionService.getExpenses(from, to), query);
        BigDecimal total = MoneyUtils.normalize(rows.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal largest = rows.stream()
                .map(Transaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new ExpenseReportView(rows.stream().map(ExpenseController::view).toList(),
                total, MoneyUtils.formatWithCurrency(total),
                largest, MoneyUtils.formatWithCurrency(largest),
                scopeOf(from, to, query));
    }

    /** البحث في البيان وحده: هو الحقل الحرّ الوحيد في صفّ المصروف */
    static List<Transaction> filtered(List<Transaction> expenses, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return expenses;
        }
        return expenses.stream()
                .filter(expense -> expense.getDescription() != null
                        && expense.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /** نفس الجملة التي تبنيها شاشة الجهاز، بنفس المفتاح: ورقتان لا تقولان شيئين */
    static String scopeOf(LocalDate from, LocalDate to, String query) {
        String needle = query == null ? "" : query.trim();
        return I18n.format("expenseReport.filterDescription", from, to,
                needle.isEmpty() ? I18n.get("common.all") : needle);
    }

    private static ExpenseView view(Transaction expense) {
        return new ExpenseView(expense.getId(), expense.getTransactionDate(),
                expense.getDescription(), expense.getAmount(),
                MoneyUtils.formatWithCurrency(expense.getAmount()));
    }
}
