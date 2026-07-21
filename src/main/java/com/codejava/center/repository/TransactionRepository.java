package com.codejava.center.repository;

import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // جلب كل حركات طالب معين مع جلب بيانات المجموعة والحصة المرتبطة لتجنب خطأ LazyInitializationException
    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.group LEFT JOIN FETCH t.session WHERE t.student.id = :studentId ORDER BY t.transactionDate DESC")
    List<Transaction> findByStudentIdOrderByTransactionDateDesc(@Param("studentId") Long studentId);

    // دالة حيوية لجرد الخزينة: حساب إجمالي الأموال لنوع معين (إيراد/مصروف) في فترة زمنية (مثلاً اليوم)
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t WHERE t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    Double sumAmountByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    boolean existsByStudentAndSessionAndType(Student student, Session session, TransactionType type);
}