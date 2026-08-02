package com.codejava.center.repository;

import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.service.dto.DailyAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // تمنع تسجيل الحضور مرتين لنفس الطالب في نفس الحصة
    boolean existsByStudentAndSession(Student student, Session session);

    long countBySession(Session session);

    // عدد الحضور لكل يوم ابتداءً من تاريخ معين - لمخطط لوحة القيادة
    @Query("""
            SELECT new com.codejava.center.service.dto.DailyAttendance(s.sessionDate, COUNT(a))
            FROM Attendance a JOIN a.session s
            WHERE s.sessionDate >= :startDate
            GROUP BY s.sessionDate
            ORDER BY s.sessionDate
            """)
    List<DailyAttendance> countAttendancePerDay(@Param("startDate") LocalDate startDate);
}