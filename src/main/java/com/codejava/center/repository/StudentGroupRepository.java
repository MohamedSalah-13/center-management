package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    /** هل الطالب مشترك فعلياً (اشتراك سارٍ) في هذه المجموعة؟ */
    boolean existsByStudentAndGroupAndIsActiveTrue(Student student, CourseGroup group);

    /** أي عضوية سابقة أو حالية - تُستخدم لتقرير إعادة التفعيل بدل إنشاء صف مكرر */
    Optional<StudentGroup> findByStudentAndGroup(Student student, CourseGroup group);

    // التعديل هنا: استخدام JOIN FETCH لجلب بيانات المجموعة والمعلم مسبقاً لتجنب خطأ LazyInitializationException
    @Query("SELECT sg FROM StudentGroup sg JOIN FETCH sg.group cg JOIN FETCH cg.teacher WHERE sg.student = :student AND sg.isActive = true")
    List<StudentGroup> findByStudentAndIsActiveTrue(@Param("student") Student student);

    /**
     * عدد المشتركين الفعليين فقط.
     * countByGroup السابقة كانت تعدّ الاشتراكات الملغاة أيضاً، فتمتلئ سعة
     * المجموعة بطلاب انسحبوا منها ولا يمكن تسجيل أحد جديد.
     */
    long countByGroupAndIsActiveTrue(CourseGroup group);
}