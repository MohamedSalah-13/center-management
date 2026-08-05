package com.codejava.center.repository;

import com.codejava.center.domain.NotificationLog;
import com.codejava.center.domain.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * معرّفات الطلاب الذين أُرسل لهم إشعار من نوع معيّن خلال فترة.
     *
     * <p>تُجلب كمجموعة واحدة لا استعلاماً لكل طالب: شاشة الإشعارات تفحص عشرات
     * الطلاب دفعةً واحدة، والفحص الفردي يعني استعلاماً لكل صف.</p>
     */
    @Query("""
            SELECT n.student.id FROM NotificationLog n
            WHERE n.type = :type AND n.sentAt >= :since
            """)
    Set<Long> findNotifiedStudentIds(@Param("type") AlertType type,
                                     @Param("since") LocalDateTime since);

    @Query("""
            SELECT n FROM NotificationLog n JOIN FETCH n.student
            WHERE n.sentAt >= :since
            ORDER BY n.sentAt DESC
            """)
    List<NotificationLog> findRecent(@Param("since") LocalDateTime since);
}
