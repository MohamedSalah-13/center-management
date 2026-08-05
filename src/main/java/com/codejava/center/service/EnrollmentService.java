package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * اشتراك الطلاب في المجموعات.
 * كان هذا المنطق موزّعاً داخل شاشة تسجيل الطلاب وتتعامل مع الـ Repository مباشرةً،
 * فكان فحص السعة والحفظ يجريان خارج أي Transaction.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final StudentGroupRepository studentGroupRepository;
    private final AuditService auditService;

    @Transactional
    public StudentGroup subscribe(Student student, CourseGroup group) {
        Optional<StudentGroup> existing = studentGroupRepository.findByStudentAndGroup(student, group);

        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalStateException(I18n.get("error.enrollment.alreadyMember"));
        }

        requireMatchingLevel(student, group);

        long currentMembers = studentGroupRepository.countByGroupAndIsActiveTrue(group);
        if (group.getMaxCapacity() != null && currentMembers >= group.getMaxCapacity()) {
            throw new IllegalStateException(
                    I18n.format("error.enrollment.groupFull", group.getMaxCapacity()));
        }

        // إعادة تفعيل اشتراك سابق بدل إنشاء صف مكرر لنفس الطالب ونفس المجموعة
        StudentGroup membership = existing.orElseGet(() -> StudentGroup.builder()
                .student(student)
                .group(group)
                .build());

        membership.setActive(true);
        membership.setJoinDate(LocalDate.now());
        // العودة تفتح مدة اشتراك جديدة: إبقاء تاريخ الخروج القديم كان سيجعل حساب
        // حصص الطالب ينتهي عند يوم خروجه الأول فيظهر بلا حضور منذ عودته
        membership.setLeaveDate(null);

        StudentGroup saved = studentGroupRepository.save(membership);
        auditService.record(AuditAction.STUDENT_ENROLLED, saved.getId(), student.getName(),
                "group=" + group.getName() + "; level=" + group.getSchoolLevel());

        return saved;
    }

    /**
     * الصف قيد قبول لا اقتراح.
     *
     * <p>الشاشة تعرض مجموعات صف الطالب وحدها، لكن القيد يُفرض هنا: الشاشة عرض،
     * والحدّ الحقيقي في طبقة الخدمات - نفس قاعدة {@code @RequiresRole}.</p>
     *
     * <p>مجموعة بلا صف (أُنشئت قبل هذه الميزة) لا تقبل أحداً حتى يُضبط صفها: قبولها
     * للجميع يعني أن الترحيل ألغى القيد بصمت عن كل مجموعة قائمة.</p>
     */
    private void requireMatchingLevel(Student student, CourseGroup group) {
        if (group.getSchoolLevel() == null) {
            throw new IllegalStateException(
                    I18n.format("error.enrollment.groupLevelMissing", group.getName()));
        }
        if (student.getSchoolLevel() == null) {
            throw new IllegalStateException(
                    I18n.format("error.enrollment.studentLevelMissing", student.getName()));
        }
        if (student.getSchoolLevel() != group.getSchoolLevel()) {
            throw new IllegalStateException(I18n.format("error.enrollment.levelMismatch",
                    student.getName(),
                    student.getSchoolLevel().getDisplayName(),
                    group.getSchoolLevel().getDisplayName()));
        }
    }

    /**
     * إنهاء اشتراك سارٍ، وتثبيت يوم الخروج.
     *
     * <p>لا يُحذف الصف: العضوية المنتهية هي ما يحدّ مدة حساب حضور الطالب، وحذفها يجعل
     * حصصه تُحسب على مدة المجموعة كلها. وهي أيضاً جواب "منذ متى لم يعد يأتي؟".</p>
     */
    @Transactional
    public StudentGroup unsubscribe(Long studentId, Long groupId) {
        StudentGroup membership = studentGroupRepository.findMembership(studentId, groupId)
                .filter(StudentGroup::isActive)
                .orElseThrow(() -> new IllegalStateException(I18n.get("error.enrollment.notMember")));

        membership.setActive(false);
        membership.setLeaveDate(LocalDate.now());

        StudentGroup saved = studentGroupRepository.save(membership);
        auditService.record(AuditAction.STUDENT_UNENROLLED, saved.getId(),
                saved.getStudent().getName(),
                "group=" + saved.getGroup().getName() + "; joined=" + saved.getJoinDate()
                        + "; left=" + saved.getLeaveDate());

        return saved;
    }

    @Transactional(readOnly = true)
    public long countActiveMembers(CourseGroup group) {
        return studentGroupRepository.countByGroupAndIsActiveTrue(group);
    }

    @Transactional(readOnly = true)
    public List<StudentGroup> getActiveGroupsOf(Student student) {
        return studentGroupRepository.findByStudentAndIsActiveTrue(student);
    }

    /** عضويات الطالب كلها - السارية والمنتهية - ومع كلٍّ حصص مدتها وما حضره منها */
    @Transactional(readOnly = true)
    public List<MembershipRow> getMemberships(Long studentId) {
        return studentGroupRepository.findStudentMemberships(studentId);
    }

    /**
     * المجموعات السارية التي لا يوافق صفُّها الصف المعطى للطالب.
     *
     * <p>قيد الصف يُفحص عند الاشتراك وحده، فتغيير مرحلة طالب مشترك يتجاوزه بابٌ خلفي.
     * ومنع التغيير خطأ - ترقية الصف في أول العام تصرّف مشروع يقع لكل طالب مرة كل سنة -
     * فالمخرج أن يُرى أثره: الشاشة تعرض المجموعات المخالفة وتطلب تأكيداً، ويبقى إنهاء
     * الاشتراك قراراً يتخذه المستخدم لا أثراً جانبياً لحفظ بيانات.</p>
     */
    @Transactional(readOnly = true)
    public List<CourseGroup> findEnrolmentsOutsideLevel(Long studentId, SchoolLevel level) {
        return studentGroupRepository.findActiveGroupsOutsideLevel(studentId, level);
    }

    /** كشف المجموعة: مشتركوها الحاليون بحضور كلٍّ داخل مدة اشتراكه */
    @Transactional(readOnly = true)
    public List<MembershipRow> getRoster(Long groupId) {
        return studentGroupRepository.findGroupRoster(groupId);
    }

    /** عدد المشتركين الفعليين لكل مجموعة، للعرض في جدول المجموعات وكشفها المطبوع */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countActiveMembersPerGroup() {
        return studentGroupRepository.countActiveMembersPerGroup().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }
}
