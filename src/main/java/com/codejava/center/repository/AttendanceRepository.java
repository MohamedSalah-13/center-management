package com.codejava.center.repository;

import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // تمنع تسجيل الحضور مرتين لنفس الطالب في نفس الحصة
    boolean existsByStudentAndSession(Student student, Session session);

    long countBySession(Session session);
}