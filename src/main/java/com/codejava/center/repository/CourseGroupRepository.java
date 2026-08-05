package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.SchoolLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {

    // تجاوز دالة الجلب الافتراضية لجلب بيانات المعلم مع المجموعة في استعلام واحد (تجنب LazyInitializationException)
    @Query("SELECT cg FROM CourseGroup cg JOIN FETCH cg.teacher")
    List<CourseGroup> findAll();

    // جلب جميع المجموعات الخاصة بمعلم معين - يُستخدم في فحص تعارض المواعيد
    List<CourseGroup> findByTeacherId(Long teacherId);

    /**
     * مجموعات صف بعينه.
     * قائمة الاشتراك في شاشة الطلاب تُبنى منها، فلا يرى الموظف مجموعةً سيرفضها النظام.
     */
    @Query("SELECT cg FROM CourseGroup cg JOIN FETCH cg.teacher WHERE cg.schoolLevel = :level ORDER BY cg.name")
    List<CourseGroup> findBySchoolLevel(@Param("level") SchoolLevel level);
}