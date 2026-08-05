package com.codejava.center.repository;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.service.dto.MembershipRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    /** هل الطالب مشترك فعلياً (اشتراك سارٍ) في هذه المجموعة؟ */
    boolean existsByStudentAndGroupAndIsActiveTrue(Student student, CourseGroup group);

    /** أي عضوية سابقة أو حالية - تُستخدم لتقرير إعادة التفعيل بدل إنشاء صف مكرر */
    Optional<StudentGroup> findByStudentAndGroup(Student student, CourseGroup group);

    /**
     * العضوية بمعرِّفَي الطالب والمجموعة، ومعها الطرفان في استعلام واحد.
     * الشاشة تعرف الأرقام لا الكيانات (الجدول مبني على {@code MembershipRow})،
     * و{@code JOIN FETCH} يجعل اسمَي الطالب والمجموعة متاحين لسجل المراقبة.
     */
    @Query("""
            SELECT sg FROM StudentGroup sg JOIN FETCH sg.student JOIN FETCH sg.group
            WHERE sg.student.id = :studentId AND sg.group.id = :groupId
            """)
    Optional<StudentGroup> findMembership(@Param("studentId") Long studentId,
                                          @Param("groupId") Long groupId);

    // التعديل هنا: استخدام JOIN FETCH لجلب بيانات المجموعة والمعلم مسبقاً لتجنب خطأ LazyInitializationException
    @Query("SELECT sg FROM StudentGroup sg JOIN FETCH sg.group cg JOIN FETCH cg.teacher WHERE sg.student = :student AND sg.isActive = true")
    List<StudentGroup> findByStudentAndIsActiveTrue(@Param("student") Student student);

    /**
     * عدد المشتركين الفعليين فقط.
     * countByGroup السابقة كانت تعدّ الاشتراكات الملغاة أيضاً، فتمتلئ سعة
     * المجموعة بطلاب انسحبوا منها ولا يمكن تسجيل أحد جديد.
     */
    long countByGroupAndIsActiveTrue(CourseGroup group);

    /** عدد المشتركين الفعليين في كل مجموعة دفعةً واحدة - كشف المجموعات يعرض العدد لكل صف */
    @Query("""
            SELECT sg.group.id, COUNT(sg)
            FROM StudentGroup sg
            WHERE sg.isActive = true
            GROUP BY sg.group.id
            """)
    List<Object[]> countActiveMembersPerGroup();

    /**
     * كشف المجموعة: مشتركوها الحاليون، ومع كلٍّ حصص مدة اشتراكه وما حضره منها.
     *
     * <p>الحصص تُعدّ من {@code joinDate} لا من إنشاء المجموعة، وتقف عند
     * {@code leaveDate} إن وُجد: هذا هو الفرق بين "حضر 4 من 5" و"حضر 4 من 30".</p>
     */
    @Query("""
            SELECT new com.codejava.center.service.dto.MembershipRow(
                st.id, st.name, st.barcode, st.phone, st.parentPhone,
                cg.id, cg.name, sg.joinDate, sg.leaveDate, sg.isActive,
                (SELECT COUNT(held) FROM Session held
                   WHERE held.group = cg AND held.sessionDate >= sg.joinDate
                     AND (sg.leaveDate IS NULL OR held.sessionDate <= sg.leaveDate)),
                (SELECT COUNT(att) FROM Attendance att
                   WHERE att.student = st AND att.session.group = cg
                     AND att.session.sessionDate >= sg.joinDate
                     AND (sg.leaveDate IS NULL OR att.session.sessionDate <= sg.leaveDate)))
            FROM StudentGroup sg JOIN sg.student st JOIN sg.group cg
            WHERE cg.id = :groupId AND sg.isActive = true
            ORDER BY st.name
            """)
    List<MembershipRow> findGroupRoster(@Param("groupId") Long groupId);

    /**
     * كل عضويات طالب، السارية والمنتهية، بنفس الحساب.
     *
     * <p>المنتهية تظهر عمداً: "لماذا لم يعد يحضر؟" جوابه سطر يقول إنه خرج من المجموعة
     * في يوم معلوم بعد أن حضر كذا حصة، لا اختفاء المجموعة من الشاشة.</p>
     */
    @Query("""
            SELECT new com.codejava.center.service.dto.MembershipRow(
                st.id, st.name, st.barcode, st.phone, st.parentPhone,
                cg.id, cg.name, sg.joinDate, sg.leaveDate, sg.isActive,
                (SELECT COUNT(held) FROM Session held
                   WHERE held.group = cg AND held.sessionDate >= sg.joinDate
                     AND (sg.leaveDate IS NULL OR held.sessionDate <= sg.leaveDate)),
                (SELECT COUNT(att) FROM Attendance att
                   WHERE att.student = st AND att.session.group = cg
                     AND att.session.sessionDate >= sg.joinDate
                     AND (sg.leaveDate IS NULL OR att.session.sessionDate <= sg.leaveDate)))
            FROM StudentGroup sg JOIN sg.student st JOIN sg.group cg
            WHERE st.id = :studentId
            ORDER BY sg.isActive DESC, cg.name
            """)
    List<MembershipRow> findStudentMemberships(@Param("studentId") Long studentId);

    /**
     * عدد المشتركين في المجموعة يوم انعقاد حصة بعينها.
     *
     * <p>يُعرض بجانب عدد الحاضرين في كشف مستحقات المعلم: العدد وحده لا يقول شيئاً،
     * و"حضر 12 من 30" يقول الكثير. لا يدخل في حساب المستحق.</p>
     *
     * <p>العضوية المنتهية قبل هذه الميزة ({@code leaveDate} فارغ و{@code isActive}
     * خطأ) لا تُعدّ: تاريخ خروجها غير معروف، وعدّها يجعل كل حصة قديمة تبدو أكبر
     * مما كانت.</p>
     */
    @Query("""
            SELECT COUNT(sg) FROM StudentGroup sg
            WHERE sg.group.id = :groupId
              AND sg.joinDate <= :date
              AND ((sg.leaveDate IS NULL AND sg.isActive = true)
                   OR (sg.leaveDate IS NOT NULL AND sg.leaveDate >= :date))
            """)
    long countEnrolledOn(@Param("groupId") Long groupId, @Param("date") LocalDate date);
}