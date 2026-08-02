package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    // التعديل هنا: استخدام JOIN FETCH لجلب بيانات المجموعة مع الحصة لتجنب خطأ LazyInitializationException في الجدول
    @Query("SELECT s FROM Session s JOIN FETCH s.group")
    List<Session> findAll();

    /**
     * كل الحصص المفتوحة حالياً (قد تكون أكثر من واحدة: قاعات متعددة تعمل بالتوازي).
     * تُستخدم لعرض قائمة الحصص المفتوحة في شاشة الحضور وشاشة إدارة الحصص.
     */
    @Query("SELECT s FROM Session s JOIN FETCH s.group g JOIN FETCH g.teacher WHERE s.isActive = true ORDER BY s.id")
    List<Session> findAllActive();

    /**
     * الحصص المفتوحة في تاريخ محدد.
     */
    @Query("SELECT s FROM Session s JOIN FETCH s.group g JOIN FETCH g.teacher WHERE s.isActive = true AND s.sessionDate = :date ORDER BY s.id")
    List<Session> findActiveByDate(@Param("date") LocalDate date);

    /**
     * الحصص المفتوحة اليوم التي ينتمي الطالب إلى مجموعاتها.
     * تُستخدم لتحديد الحصة تلقائياً عند مسح الكارنيه حين يخدم جهاز واحد كل القاعات.
     * قد ترجع أكثر من حصة لو كان الطالب مشتركاً في مجموعتين مفتوحتين في نفس الوقت،
     * وحينها يجب أن يختار الموظف الحصة يدوياً.
     */
    @Query("""
            SELECT s FROM Session s JOIN FETCH s.group g JOIN FETCH g.teacher
            WHERE s.isActive = true AND s.sessionDate = :date
              AND EXISTS (SELECT 1 FROM StudentGroup sg
                          WHERE sg.group = g AND sg.student = :student AND sg.isActive = true)
            ORDER BY s.id
            """)
    List<Session> findActiveForStudent(@Param("student") Student student, @Param("date") LocalDate date);

    /**
     * هل لهذه المجموعة حصة مفتوحة بالفعل؟
     * القيد الوحيد الباقي: المجموعة الواحدة لا يمكن أن يكون لها حصتان مفتوحتان في آن واحد.
     * أما المجموعات المختلفة فيجوز أن تعمل بالتوازي في قاعات مختلفة.
     */
    Optional<Session> findByGroupAndIsActiveTrue(CourseGroup group);

    /**
     * الحصص القابلة لصرف مستحقات معلمها: مغلقة ولم تُصرَف بعد.
     * الحصة المفتوحة مستبعدة عمداً لأن طلاباً آخرين قد يسجّلون حضورهم بعد،
     * فيُحتسب الإيراد ناقصاً ويُصرف للمعلم أقل من حقه.
     */
    @Query("""
            SELECT s FROM Session s JOIN FETCH s.group g JOIN FETCH g.teacher
            WHERE s.isActive = false AND s.isPaidOut = false
            ORDER BY s.sessionDate, s.id
            """)
    List<Session> findPayableSessions();

    /** عدد الحصص المنعقدة لمجموعة خلال فترة - المقام في نسبة الحضور */
    long countByGroupIdAndSessionDateBetween(Long groupId, LocalDate fromDate, LocalDate toDate);

    /**
     * جلب حصة مع مجموعتها ومعلمها في استعلام واحد (لتفادي LazyInitializationException خارج الـ Transaction)
     */
    @Query("SELECT s FROM Session s JOIN FETCH s.group g JOIN FETCH g.teacher WHERE s.id = :id")
    Optional<Session> findByIdWithGroup(@Param("id") Long id);
}
