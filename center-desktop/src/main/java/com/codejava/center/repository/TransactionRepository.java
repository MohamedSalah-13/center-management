package com.codejava.center.repository;

import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.service.dto.GroupRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // جلب كل حركات طالب معين مع جلب بيانات المجموعة والحصة المرتبطة لتجنب خطأ LazyInitializationException
    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.group LEFT JOIN FETCH t.session WHERE t.student.id = :studentId ORDER BY t.transactionDate DESC")
    List<Transaction> findByStudentIdOrderByTransactionDateDesc(@Param("studentId") Long studentId);

    // دالة حيوية لجرد الخزينة: حساب إجمالي الأموال لنوع معين (إيراد/مصروف) في فترة زمنية (مثلاً اليوم)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    BigDecimal sumAmountByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    boolean existsByStudentAndSessionAndType(Student student, Session session, TransactionType type);

    /**
     * حركات نقدية في فترة، لعرضها في شاشة تقفيل الوردية.
     * تستثني SESSION_CHARGE لأنها استحقاق على الطالب لا حركة في الدرج.
     * LEFT JOIN FETCH لأن الحركات العامة (مصروفات، مستحقات معلمين) بلا طالب أو مجموعة.
     */
    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN FETCH t.student LEFT JOIN FETCH t.group
            WHERE t.type <> com.codejava.center.domain.enums.TransactionType.SESSION_CHARGE
              AND t.transactionDate >= :startDate AND t.transactionDate < :endDate
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findCashMovements(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * مصروفات فترة، الأحدث أولاً.
     *
     * <p>غير {@link #findCashMovements}: ذاك يجمع كل ما تحرّك في الدرج ليوم واحد -
     * وارداً ومنصرفاً ومستحقاتٍ مصروفة - وهذا نوع واحد على امتداد شهر أو أكثر. ولذلك
     * التصفية بالنوع في الاستعلام لا في الذاكرة: قراءة حركات سنة كاملة لتُرمى تسعة
     * أعشارها هي ما كانت شاشة المصروفات تفعله ليومها الواحد.</p>
     *
     * <p>لا {@code JOIN FETCH}: المصروف حركة عامة بلا طالب ولا مجموعة.</p>
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.type = com.codejava.center.domain.enums.TransactionType.EXPENSE
              AND t.transactionDate >= :startDate AND t.transactionDate < :endDate
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findExpenses(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * رصيد الطالب = مجموع ما دفعه (INCOME) ناقص مجموع رسوم الحصص المخصومة (SESSION_CHARGE).
     * الحركات السابقة لتاريخ بداية دفتر الحسابات تُستبعد، لأن الدفعات القديمة
     * ليست لها خصومات مقابلة وستُظهر الطالب دائناً بمبلغ وهمي.
     * تمرير null في startDate يعني احتساب كل الحركات.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                     THEN t.amount ELSE -t.amount END), 0)
            FROM Transaction t
            WHERE t.student.id = :studentId
              AND t.type IN (com.codejava.center.domain.enums.TransactionType.INCOME,
                             com.codejava.center.domain.enums.TransactionType.SESSION_CHARGE)
              AND (:startDate IS NULL OR t.transactionDate >= :startDate)
            """)
    BigDecimal calculateStudentBalance(@Param("studentId") Long studentId,
                                       @Param("startDate") LocalDateTime startDate);

    // إجمالي الإيراد لكل مجموعة خلال فترة - لمخطط لوحة القيادة
    @Query("""
            SELECT new com.codejava.center.service.dto.GroupRevenue(g.name, SUM(t.amount))
            FROM Transaction t JOIN t.group g
            WHERE t.type = com.codejava.center.domain.enums.TransactionType.INCOME
              AND t.transactionDate >= :startDate AND t.transactionDate < :endDate
            GROUP BY g.name
            ORDER BY SUM(t.amount) DESC
            """)
    List<GroupRevenue> sumIncomeByGroup(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
}