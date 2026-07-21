package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {

    // جلب جميع المجموعات الخاصة بمعلم معين
    List<CourseGroup> findByTeacherId(Long teacherId);
}