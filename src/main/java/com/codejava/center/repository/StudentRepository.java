package com.codejava.center.repository;

import com.codejava.center.domain.Student;
import com.codejava.center.service.dto.StudentBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    // سحر Spring Data: سيكتب استعلام البحث بالباركود آلياً بناءً على اسم الدالة
    Optional<Student> findByBarcode(String barcode);

    // البحث عن الطلاب بالاسم (لشريط البحث السريع)
    List<Student> findByNameContainingIgnoreCase(String name);

    boolean existsByName(String name);

    /**
     * الطلاب النشطون الذين عليهم متأخرات (رصيد سالب)، مرتّبين من الأكثر مديونية.
     *
     * <p>يُحسب الرصيد بنفس معادلة {@code calculateStudentBalance}: المدفوع ناقص رسوم
     * الحصص، مع استبعاد ما قبل تاريخ بداية دفتر الحسابات. LEFT JOIN حتى يظهر الطالب
     * الذي لم تُسجَّل له أي حركة، وشروط النوع والتاريخ في ON لا في WHERE وإلا تحوّل
     * الربط إلى INNER وسقط هؤلاء الطلاب.</p>
     */
    @Query("""
            SELECT new com.codejava.center.service.dto.StudentBalance(
                s.id, s.name, s.barcode, s.phone, s.parentPhone,
                COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                  THEN t.amount ELSE -t.amount END), 0))
            FROM Student s
            LEFT JOIN Transaction t ON t.student = s
                 AND t.type IN (com.codejava.center.domain.enums.TransactionType.INCOME,
                                com.codejava.center.domain.enums.TransactionType.SESSION_CHARGE)
                 AND (:startDate IS NULL OR t.transactionDate >= :startDate)
            WHERE s.isActive = true
            GROUP BY s.id, s.name, s.barcode, s.phone, s.parentPhone
            HAVING COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                     THEN t.amount ELSE -t.amount END), 0) < 0
            ORDER BY COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                       THEN t.amount ELSE -t.amount END), 0)
            """)
    List<StudentBalance> findStudentsInArrears(@Param("startDate") LocalDateTime startDate);
}