package com.codejava.center.repository;

import com.codejava.center.domain.Alert;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * صندوق التنبيهات.
 *
 * <p>يرث {@code JpaRepository} كاملاً - على عكس {@code AuditLogRepository} - لأن هذا
 * الجدول ليس سجلّ إثبات: التنبيه رسالة تشغيلية تُقرأ وتُعالَج، ومسح المعالَج القديم
 * تنظيفٌ لا إخفاءُ أدلّة. ما يجب أن يبقى للإثبات مكتوب في {@code audit_logs}
 * و{@code notification_logs}، وكلاهما لا يُحذف منه.</p>
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * هل أُطلق هذا التنبيه في نافذة التهدئة الحالية؟
     *
     * <p>فحصٌ مسبق يوفّر محاولة إدراج فاشلة في الحالة الغالبة، وليس هو الضمان: الضمان
     * هو القيد الفريد على العمود نفسه. بين هذا الفحص والإدراج فجوةٌ يمرّ منها جهازان
     * يصحوان على نفس الموعد، وهي بالضبط الحالة التي يوجد القيد من أجلها.</p>
     */
    boolean existsByDedupeKey(String dedupeKey);

    /** عدد ما لم يُعالَج بعد، لشارة القائمة الجانبية */
    long countByAcknowledgedAtIsNull();

    /**
     * ما استُجدّ بعد المعرّف المعطى ولم يُعالَج بعد - مصدر الإشعارات المنبثقة.
     *
     * <p>بالمعرّف لا بالوقت: ساعات الأجهزة في السنتر لا تتطابق، وفحصٌ يقارن
     * {@code raisedAt} بلحظة قرأها جهازٌ ساعته متقدمة بدقيقة يتخطّى تنبيهات كتبها جاره
     * فعلاً. المعرّف تصاعديّ يمنحه الخادم نفسه، فلا يعتمد على ساعة أحد.</p>
     *
     * <p>وهو ما يجعل الجهاز يرى ما فحصه جهازٌ آخر: المجدوِل يعمل على كل الأجهزة لكن
     * القيد الفريد يجعل أولها يكسب، فلولا هذه القراءة لَما عرف الباقون شيئاً.</p>
     */
    @Query("""
            SELECT a FROM Alert a
            WHERE a.id > :afterId AND a.acknowledgedAt IS NULL
            ORDER BY a.id
            """)
    List<Alert> findRaisedAfter(@Param("afterId") long afterId, Pageable pageable);

    /**
     * أكبر معرّف موجود الآن. يُقرأ عند فتح لوحة القيادة ليصير خط البداية: بدونه ينهال
     * على المستخدم عند كل تسجيل دخول كل ما تراكم في أسبوع دفعةً واحدة.
     */
    @Query("SELECT COALESCE(MAX(a.id), 0) FROM Alert a")
    long findHighestId();

    /**
     * الكيانات التي أُطلق لها تنبيه من هذا النوع منذ لحظة - أي نافذة التهدئة كما هي،
     * لا مقرَّبة إلى دلو.
     *
     * <p>الحاجز الثاني إلى جانب {@code dedupe_key}، وكلٌّ منهما يسدّ ثغرة الآخر: المفتاح
     * الفريد يقسّم الزمن إلى فترات ثابتة فيسمح بتنبيهين على طرفَي حدّ فترتين، وهذا
     * يمنعه لأنه يقيس المدة من التنبيه السابق نفسه؛ وهذا فحصٌ في الذاكرة تمرّ من فجوته
     * أجهزةٌ تفحص في نفس اللحظة، والمفتاح الفريد يمنعها.</p>
     *
     * <p>مجموعةً واحدة لكل قاعدة لا استعلاماً لكل حالة: قائمة المتأخرات وحدها قد تحمل
     * مئتَي طالب. وقد تحوي القائمة {@code null} لتنبيهات لا كيان لها (فشل نسخة
     * احتياطية)، وهو ما يجعل الفحص يشملها بلا حالة خاصة.</p>
     */
    @Query("""
            SELECT a.entityId FROM Alert a
            WHERE a.type = :type AND a.raisedAt >= :since
            """)
    List<Long> findRecentEntityIds(@Param("type") AlertType type,
                                   @Param("since") LocalDateTime since);

    /**
     * تنبيهات فترة، مصفّاة اختيارياً بالنوع وبالدرجة.
     *
     * <p>{@code types} و{@code severities} تُمرَّران دائماً غير فارغتين - كل القيم حين لا
     * يختار المستخدم تصفية - بدل شرط {@code :types IS NULL}: مقارنة معامل من نوع قائمة
     * بـ NULL سلوكها غير محدَّد في JPQL، وهي نفس القاعدة المتّبعة في بحث سجل المراقبة.</p>
     *
     * <p>الترتيب: غير المعالَج أولاً ثم الأحدث. التنبيه المعالَج يبقى معروضاً لأن
     * "متى انتهت هذه المشكلة" سؤال يُطرح بعد أسبوع.</p>
     */
    @Query("""
            SELECT a FROM Alert a
            WHERE a.raisedAt >= :from AND a.raisedAt < :to
              AND a.type IN :types
              AND a.severity IN :severities
            ORDER BY CASE WHEN a.acknowledgedAt IS NULL THEN 0 ELSE 1 END,
                     a.raisedAt DESC, a.id DESC
            """)
    List<Alert> search(@Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("types") Collection<AlertType> types,
                       @Param("severities") Collection<AlertSeverity> severities,
                       Pageable pageable);

    /** عدد ما يطابق نفس الشروط، لتعرف الشاشة أن ما تعرضه مقصوص */
    @Query("""
            SELECT COUNT(a) FROM Alert a
            WHERE a.raisedAt >= :from AND a.raisedAt < :to
              AND a.type IN :types
              AND a.severity IN :severities
            """)
    long countMatching(@Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("types") Collection<AlertType> types,
                       @Param("severities") Collection<AlertSeverity> severities);
}
