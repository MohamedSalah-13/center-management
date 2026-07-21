package com.codejava.center.repository;

import com.codejava.center.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    // سحر Spring Data: سيكتب استعلام البحث بالباركود آلياً بناءً على اسم الدالة
    Optional<Student> findByBarcode(String barcode);

    // البحث عن الطلاب بالاسم (لشريط البحث السريع)
    List<Student> findByNameContainingIgnoreCase(String name);

    boolean existsByName(String name);
}