package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.WeekDays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseGroupService {

    private final CourseGroupRepository courseGroupRepository;
    private final AuditService auditService;

    @Transactional
    @RequiresRole(Role.ADMIN)
    public CourseGroup saveGroup(CourseGroup group) {
        if (group.getTeacher() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.teacherRequired"));
        }
        if (group.getMaxCapacity() == null || group.getMaxCapacity() <= 0) {
            throw new IllegalArgumentException(I18n.get("error.group.capacityPositive"));
        }
        if (group.getSessionPrice() == null || group.getSessionPrice().signum() < 0) {
            throw new IllegalArgumentException(I18n.get("error.group.priceNegative"));
        }
        // الصف شرط قبول الطالب في المجموعة؛ مجموعة بلا صف تقبل الجميع، وهو نقيض المطلوب
        if (group.getSchoolLevel() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.levelRequired"));
        }
        if (group.getMeetingDays() == null || group.getMeetingDays().isEmpty()) {
            throw new IllegalArgumentException(I18n.get("error.group.daysRequired"));
        }
        if (group.getStartTime() == null || group.getEndTime() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.timeRequired"));
        }
        if (!group.getEndTime().isAfter(group.getStartTime())) {
            throw new IllegalArgumentException(I18n.get("error.group.endBeforeStart"));
        }

        rejectIfTeacherIsBusy(group);

        group.setSessionPrice(MoneyUtils.normalize(group.getSessionPrice()));
        applyName(group);

        boolean isNew = group.getId() == null;
        CourseGroup saved = courseGroupRepository.save(group);

        // سعر الحصة يُسجَّل مع كل تعديل: هو ما يُخصم من رصيد كل طالب عند حضوره،
        // وتخفيضه ثم إعادته بعد الحصة تغيير مالي لا يترك أثراً في جدول الحركات.
        // والموعد يُسجَّل معه لأن تغييره ينقل المعلم من ساعة إلى ساعة في كشف حسابه.
        auditService.record(isNew ? AuditAction.GROUP_CREATED : AuditAction.GROUP_UPDATED,
                saved.getId(), saved.getName(),
                "price=" + MoneyUtils.format(saved.getSessionPrice())
                        + "; capacity=" + saved.getMaxCapacity()
                        + "; level=" + saved.getSchoolLevel()
                        + "; days=" + daysAsText(saved.getMeetingDays())
                        + "; time=" + saved.getStartTime() + "-" + saved.getEndTime());

        // تُعاد المجموعة ومعلمها محمَّلاً: العائد من save بعد merge يحمل وكيلاً كسولاً
        // للمعلم، والشاشة تقرأ اسمه بعد إغلاق المعاملة - انظر findByIdWithTeacher
        return courseGroupRepository.findByIdWithTeacher(saved.getId()).orElse(saved);
    }

    /**
     * المعلم لا يكون في قاعتين في وقت واحد.
     *
     * <p>الفحص محصور في مجموعات هذا المعلم: التوقيت الواحد لمعلمين مختلفين هو الوضع
     * الطبيعي في سنتر له أكثر من قاعة، ومنعه كان سيمنع نصف الجدول.</p>
     *
     * <p>يُستثنى الصف نفسه عند التعديل، وإلا تعارضت المجموعة مع نسختها المحفوظة فلم
     * يعد ممكناً تغيير سعرها.</p>
     */
    private void rejectIfTeacherIsBusy(CourseGroup group) {
        List<CourseGroup> sameTeacher = courseGroupRepository.findByTeacherId(group.getTeacher().getId());

        for (CourseGroup other : sameTeacher) {
            if (other.getId().equals(group.getId())) {
                continue;
            }
            if (GroupSchedule.conflicts(group, other)) {
                throw new IllegalStateException(I18n.format("error.group.teacherBusy",
                        group.getTeacher().getName(),
                        WeekDays.describe(GroupSchedule.sharedDays(group, other)),
                        WeekDays.describeRange(other.getStartTime(), other.getEndTime()),
                        other.getName()));
            }
        }
    }

    /**
     * الاسم المشتق يُعاد بناؤه عند كل حفظ ما لم يُوقفه المستخدم صراحةً،
     * فلا يبقى اسم يقول "السبت الرابعة" لمجموعة نُقلت إلى الاثنين.
     */
    private void applyName(CourseGroup group) {
        if (group.isAutoName()) {
            group.setName(GroupSchedule.compose(group));
            return;
        }
        if (group.getName() == null || group.getName().isBlank()) {
            throw new IllegalArgumentException(I18n.get("error.group.nameRequired"));
        }
        group.setName(group.getName().trim());
    }

    /** أيام المجموعة بأسمائها الثابتة لسجل المراقبة - لا نص مترجَم يُخزَّن */
    private String daysAsText(Set<DayOfWeek> days) {
        return days == null || days.isEmpty()
                ? "-"
                : days.stream().sorted(WeekDays.weekOrder()).map(DayOfWeek::name)
                        .reduce((a, b) -> a + "," + b).orElse("-");
    }

    @Transactional(readOnly = true)
    public List<CourseGroup> getAllGroups() {
        return courseGroupRepository.findAll();
    }

    /** المجموعات التي تخدم صفاً بعينه - قائمة الاشتراك في شاشة الطلاب تُبنى منها */
    @Transactional(readOnly = true)
    public List<CourseGroup> getGroupsOfLevel(SchoolLevel level) {
        return level == null ? List.of() : courseGroupRepository.findBySchoolLevel(level);
    }

    /**
     * حذف مجموعة دراسية
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void deleteGroup(Long groupId) {
        // يمكنك هنا إضافة تحقق للـ Constraints (مثلاً هل يوجد طلاب مسجلين في المجموعة؟)
        // قبل السماح بالحذف لتجنب الـ DataIntegrityViolationException
        Optional<String> name = courseGroupRepository.findById(groupId).map(CourseGroup::getName);

        courseGroupRepository.deleteById(groupId);
        auditService.record(AuditAction.GROUP_DELETED, groupId, name.orElse(null));
    }

}
