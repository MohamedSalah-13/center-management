package com.codejava.center.repository;

import com.codejava.center.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    // التعديل هنا: استخدام JOIN FETCH لجلب بيانات المجموعة مع الحصة لتجنب خطأ LazyInitializationException في الجدول
    @Query("SELECT s FROM Session s JOIN FETCH s.group")
    List<Session> findAll();

    // جلب الحصة المفتوحة حالياً
    @Query("SELECT s FROM Session s WHERE s.isActive = true")
    Optional<Session> findActiveSessionForToday();
}