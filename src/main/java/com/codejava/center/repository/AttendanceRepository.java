package com.codejava.center.repository;

import com.codejava.center.domain.Attendance;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.DailyAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // تمنع تسجيل الحضور مرتين لنفس الطالب في نفس الحصة
    boolean existsByStudentAndSession(Student student, Session session);

    /**
     * صفّ الطالب في هذه الحصة إن وُجد.
     *
     * <p>{@code existsBy...} لم تعد تكفي في مسار التمرير: التمريرة الثانية صارت انصرافاً،
     * وتقريرُ ذلك يحتاج الصفَّ نفسه - متى دخل، وهل سُجّل انصرافه بالفعل - لا مجرّد
     * علمٍ بأنه موجود.</p>
     */
    Optional<Attendance> findByStudentAndSession(Student student, Session session);

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

    /**
     * كشف الحضور والانصراف: صفٌّ لكل طالب في كل حصة خلال الفترة.
     *
     * <p>التصفية في قاعدة البيانات لا في الذاكرة: {@code attendances} أسرع جداول السنتر
     * نمواً - صفٌّ لكل طالب في كل حصة - فجلبه كاملاً ليُصفّى بعد ذلك يثقل الشاشة أكثر
     * كلما طال عمر السنتر، وهو نفس سبب {@code SessionRepository.findFiltered}.</p>
     *
     * <p>الترتيب: الأحدث دخولاً أولاً، فمن يُسأل عنه على الشاشة هو من دخل قبل قليل.</p>
     *
     * @param groupId مجموعة بعينها، أو {@code null} لكل المجموعات
     */
    @Query("""
            SELECT new com.codejava.center.service.dto.AttendanceLogRow(
                       a.id, st.name, st.barcode, st.parentPhone, g.name,
                       s.sessionDate, a.timeIn, a.timeOut, s.isActive)
            FROM Attendance a
                JOIN a.session s
                JOIN s.group g
                JOIN a.student st
            WHERE s.sessionDate BETWEEN :from AND :to
              AND (:groupId IS NULL OR g.id = :groupId)
            ORDER BY a.timeIn DESC, a.id DESC
            """)
    List<AttendanceLogRow> findAttendanceLog(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("groupId") Long groupId);
}