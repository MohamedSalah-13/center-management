package com.codejava.center.repository;

import com.codejava.center.domain.Student;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.StudentBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * الطلاب المسجَّلون حالياً - وهذا ما تقرأه شاشة التسجيل، لا {@code findAll()}.
     *
     * <p>جدول الطلاب تراكمي بطبعه: الحذف تمنعه المفاتيح الأجنبية لكل من له حضور أو
     * حركة مالية، أي لكل من درس فعلاً، فيبقى كل من مرّ بالسنتر منذ افتتاحه في الجدول.
     * قراءتهم جميعاً عند كل فتح للشاشة تكبر بلا حدّ سنةً بعد سنة، بينما ما يعني الموظف
     * أمام الجهاز هو طلاب العام الجاري.</p>
     *
     * <p>استعلام صريح لا اشتقاق من اسم الدالة: الحقل {@code isActive} وقارئه
     * {@code isActive()}، وهو بالضبط الموضع الذي يفشل فيه اشتقاق أسماء الخصائص بهدوء.</p>
     */
    @Query("SELECT s FROM Student s WHERE s.isActive = true ORDER BY s.name")
    List<Student> findActive();

    /** الجميع، والمؤرشفون آخراً: من يطلب رؤيتهم يريد الحاليين أمام عينه أولاً */
    @Query("SELECT s FROM Student s ORDER BY s.isActive DESC, s.name")
    List<Student> findAllOrdered();

    /** عدد المسجَّلين حالياً: عدٌّ في القاعدة لا قائمةٌ تُقرأ كاملةً ليُقاس طولها */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.isActive = true")
    long countActive();

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

    /**
     * الطلاب النشطون الذين أوشك رصيدهم على النفاد: موجب بعد، وأقل من الحدّ.
     *
     * <p>نفس معادلة {@code findStudentsInArrears} بالضبط، وما يفرقهما شرط
     * {@code HAVING}: هناك أقلّ من صفر (عليه مستحقات)، وهنا بين صفر والحدّ (سيقف على
     * البوابة بعد حصة أو حصتين). الفصل بينهما مقصود - التنبيه قبل نفاد الرصيد يمنع
     * الموقف، والتنبيه بعده يعالجه، ولكلٍّ نصّه ودرجته.</p>
     *
     * <p>ويشترط اشتراكاً سارياً في مجموعة: طالب مسجَّل ولم يلتحق بأي مجموعة رصيده صفر
     * دائماً، فيملأ التنبيه بأسماء لا مشكلة فيها.</p>
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
              AND EXISTS (SELECT 1 FROM StudentGroup sg
                          WHERE sg.student = s AND sg.isActive = true)
            GROUP BY s.id, s.name, s.barcode, s.phone, s.parentPhone
            HAVING COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                     THEN t.amount ELSE -t.amount END), 0) >= 0
               AND COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                     THEN t.amount ELSE -t.amount END), 0) < :maxBalance
            ORDER BY COALESCE(SUM(CASE WHEN t.type = com.codejava.center.domain.enums.TransactionType.INCOME
                                       THEN t.amount ELSE -t.amount END), 0)
            """)
    List<StudentBalance> findStudentsWithLowBalance(@Param("startDate") LocalDateTime startDate,
                                                    @Param("maxBalance") BigDecimal maxBalance);

    /**
     * طلاب نشطون لهم اشتراك سارٍ ولم يُسجَّل لهم حضور منذ التاريخ المعطى.
     *
     * <p>الانقطاع غير الغياب: الغياب يُقاس داخل مجموعة وفترة تقرير، وهذا يقول إن
     * الطالب اختفى من السنتر كله. اشتراط الاشتراك السارِي ضروري وإلا ظهر كل من سُجّل
     * ولم يلتحق بمجموعة بعد على أنه منقطع.</p>
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.isActive = true
              AND EXISTS (SELECT 1 FROM StudentGroup sg
                          WHERE sg.student = s AND sg.isActive = true)
              AND NOT EXISTS (SELECT 1 FROM Attendance a
                              WHERE a.student = s AND a.session.sessionDate >= :since)
            ORDER BY s.name
            """)
    List<Student> findInactiveSince(@Param("since") LocalDate since);

    /**
     * حضور كل طالب في مجموعة خلال فترة.
     *
     * <p>النظام يسجّل الحضور فقط ولا يوجد جدول للغياب، فالغياب يُستنتج: كل مشترك نشط
     * في المجموعة يظهر هنا ولو لم يحضر ولا حصة واحدة (LEFT JOIN)، وعدد غيابه هو
     * إجمالي حصص الفترة ناقص ما حضره.</p>
     *
     * <p>شرط نطاق الحصص داخل ON لا WHERE: وضعه في WHERE يحوّل الربط إلى INNER
     * فيختفي الطلاب الغائبون تماماً وهم بالضبط من يبحث عنهم التقرير.</p>
     */
    @Query("""
            SELECT new com.codejava.center.service.dto.AttendanceSummary(
                st.id, st.name, st.barcode, st.parentPhone, COUNT(a.id))
            FROM Student st
            JOIN StudentGroup sg ON sg.student = st AND sg.group.id = :groupId AND sg.isActive = true
            LEFT JOIN Attendance a ON a.student = st
                 AND a.session.group.id = :groupId
                 AND a.session.sessionDate BETWEEN :fromDate AND :toDate
            WHERE st.isActive = true
            GROUP BY st.id, st.name, st.barcode, st.parentPhone
            ORDER BY COUNT(a.id), st.name
            """)
    List<AttendanceSummary> findGroupAttendance(@Param("groupId") Long groupId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);
}