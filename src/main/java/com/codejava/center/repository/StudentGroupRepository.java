package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    // تتحقق مما إذا كان الطالب مشتركاً في هذه المجموعة (ترجع true أو false)
    boolean existsByStudentAndGroup(Student student, CourseGroup group);

    // داخل StudentGroupRepository.java
    List<StudentGroup> findByStudentAndIsActiveTrue(Student student);
}