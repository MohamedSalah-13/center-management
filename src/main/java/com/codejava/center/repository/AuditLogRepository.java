package com.codejava.center.repository;

import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.enums.AuditAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * سجل المراقبة: إضافة وقراءة فقط.
 *
 * <p><b>لا يرث {@code JpaRepository} عن قصد</b>، بل {@code Repository} المجرَّدة التي لا
 * تعلن أي دالة. الوراثة من {@code JpaRepository} كانت ستمنح البرنامج
 * {@code delete} و{@code deleteAll} على سجل غرضه كله أن يكون غير قابل للمحو، ويكفي
 * استدعاء واحد في مكان واحد ليضيع أثر ما جرى. المعلَن هنا هو ما يُسمح به فعلاً:
 * {@code save} والقراءة.</p>
 *
 * <p>وحدوده معروفة: هو حاجز داخل البرنامج لا داخل قاعدة البيانات. مستخدم القاعدة يملك
 * {@code DELETE} و{@code DROP} على {@code center_db} بالضرورة - تحتاجهما Flyway
 * والاستعادة، راجع {@code docs/first-install.md} §3 - فمن يصل إلى كلمة مروره يستطيع
 * المحو بعميل SQL. ما يمنعه هذا الصف هو أن يصير المحو ميزةً في الشاشة يستعملها من
 * أمام الجهاز.</p>
 */
@Repository
public interface AuditLogRepository extends org.springframework.data.repository.Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);

    /**
     * أحداث فترة زمنية، مصفّاة اختيارياً بالمستخدم، مرتّبة من الأحدث.
     *
     * <p>{@code actions} تُمرَّر دائماً غير فارغة - كل الأنواع حين لا يختار المستخدم
     * تصنيفاً - بدل شرط {@code :actions IS NULL}: مقارنة معامل من نوع قائمة بـ NULL
     * سلوكها غير محدَّد في JPQL.</p>
     *
     * <p>الترتيب الثانوي بالمعرّف ضروري: عدة أحداث في نفس الجزء من الثانية (حفظ يتبعه
     * حفظ) كان ترتيبها بينها غير ثابت، فتقفز الأسطر بين تحديث وآخر لنفس الفترة.</p>
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.occurredAt >= :from AND a.occurredAt < :to
              AND (:actor IS NULL OR a.actorUsername = :actor)
              AND a.action IN :actions
            ORDER BY a.occurredAt DESC, a.id DESC
            """)
    List<AuditLog> search(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("actor") String actor,
                          @Param("actions") Collection<AuditAction> actions,
                          Pageable pageable);

    /** عدد ما يطابق نفس الشروط، لتعرف الشاشة أن ما تعرضه مقصوص */
    @Query("""
            SELECT COUNT(a) FROM AuditLog a
            WHERE a.occurredAt >= :from AND a.occurredAt < :to
              AND (:actor IS NULL OR a.actorUsername = :actor)
              AND a.action IN :actions
            """)
    long countMatching(@Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("actor") String actor,
                       @Param("actions") Collection<AuditAction> actions);

    /**
     * أسماء من له أثر في السجل، لملء قائمة التصفية.
     * تُقرأ من السجل نفسه لا من جدول المستخدمين: المستخدم المحذوف تصرّفاته باقية
     * ويجب أن يظل ممكناً استعراضها.
     */
    @Query("""
            SELECT DISTINCT a.actorUsername FROM AuditLog a
            WHERE a.actorUsername IS NOT NULL
            ORDER BY a.actorUsername
            """)
    List<String> findDistinctActors();
}
