package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {

    // تجاوز دالة الجلب الافتراضية لجلب بيانات المعلم مع المجموعة في استعلام واحد (تجنب LazyInitializationException)
    @Query("SELECT cg FROM CourseGroup cg JOIN FETCH cg.teacher")
    List<CourseGroup> findAll();

    // جلب جميع المجموعات الخاصة بمعلم معين
    List<CourseGroup> findByTeacherId(Long teacherId);
}