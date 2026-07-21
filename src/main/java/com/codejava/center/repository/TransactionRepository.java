package com.codejava.center.repository;

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

    // جلب كل حركات طالب معين
    List<Transaction> findByStudentIdOrderByTransactionDateDesc(Long studentId);

    // دالة حيوية لجرد الخزينة: حساب إجمالي الأموال لنوع معين (إيراد/مصروف) في فترة زمنية (مثلاً اليوم)
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t WHERE t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    Double sumAmountByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}