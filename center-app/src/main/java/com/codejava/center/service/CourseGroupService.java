package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.service.dto.CourseGroupDraft;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.WeekDays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseGroupService {

    private final CourseGroupRepository courseGroupRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    /**
     * مجموعةٌ بمعرّفها، ومعلّمُها محمَّلٌ معها.
     *
     * <p>لازمةٌ لمن يصل إليها برقمٍ لا بكائن - أي كلُّ حافة HTTP: شاشةُ سطح المكتب
     * تحمل الصفَّ الذي اختاره المستخدم من قائمةٍ قرأتها توّاً، والطلبُ لا يحمل إلا
     * رقماً في مساره.</p>
     *
     * <p>وبـ {@code JOIN FETCH} على المعلم: الاسم يُقرأ بعد إغلاق المعاملة - في رسالة
     * تعارضٍ أو في صفّ جدول - وعلاقةٌ كسولة هناك تعني
     * {@code LazyInitializationException} لا حقلاً فارغاً.</p>
     *
     * <p>قراءةٌ بلا حارس، كبقية القراءات: ما تدين به الحافة للقراءة هو المصادقة لا
     * التصريح.</p>
     */
    @Transactional(readOnly = true)
    public CourseGroup findById(Long id) {
        return courseGroupRepository.findByIdWithTeacher(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.group.notFound")));
    }

    /**
     * حفظ مجموعة جديدة أو تعديل قائمة.
     *
     * <p>المدخل {@link CourseGroupDraft} لا الكيان: المعلم يصل رقماً ويُقرأ صفُّه من
     * القاعدة - انظر تعليق المسودة - والصفُّ القائم يُقرأ ثم يُطبَّق عليه ما فيها،
     * فما ليست المسودةُ صاحبةَ قراره لا يتغير.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public CourseGroup saveGroup(CourseGroupDraft draft) {
        if (draft.teacherId() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.teacherRequired"));
        }
        if (draft.maxCapacity() == null || draft.maxCapacity() <= 0) {
            throw new IllegalArgumentException(I18n.get("error.group.capacityPositive"));
        }
        if (draft.sessionPrice() == null || draft.sessionPrice().signum() < 0) {
            throw new IllegalArgumentException(I18n.get("error.group.priceNegative"));
        }
        // الصف شرط قبول الطالب في المجموعة؛ مجموعة بلا صف تقبل الجميع، وهو نقيض المطلوب
        if (draft.schoolLevel() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.levelRequired"));
        }
        if (draft.meetingDays() == null || draft.meetingDays().isEmpty()) {
            throw new IllegalArgumentException(I18n.get("error.group.daysRequired"));
        }
        if (draft.startTime() == null || draft.endTime() == null) {
            throw new IllegalArgumentException(I18n.get("error.group.timeRequired"));
        }
        if (!draft.endTime().isAfter(draft.startTime())) {
            throw new IllegalArgumentException(I18n.get("error.group.endBeforeStart"));
        }

        Teacher teacher = teacherRepository.findById(draft.teacherId())
                .orElseThrow(() -> new IllegalStateException(I18n.get("error.teacher.notFound")));

        boolean isNew = draft.isNew();
        CourseGroup group = isNew
                ? CourseGroup.builder().build()
                : courseGroupRepository.findById(draft.id())
                        .orElseThrow(() -> new IllegalStateException(I18n.get("error.group.notFound")));

        group.setTeacher(teacher);
        group.setSchoolLevel(draft.schoolLevel());
        group.setMaxCapacity(draft.maxCapacity());
        // نسخةٌ خاصة بالكيان: مجموعةٌ واصلةٌ من جسم طلب قد تكون غير قابلة للتعديل،
        // والترتيب يُحفظ كما اختاره المستخدم لأن المحوِّل يكتبها نصاً واحداً
        group.setMeetingDays(new LinkedHashSet<>(draft.meetingDays()));
        group.setStartTime(draft.startTime());
        group.setEndTime(draft.endTime());
        group.setAutoName(draft.autoName());
        group.setName(draft.name());
        group.setSessionPrice(MoneyUtils.normalize(draft.sessionPrice()));
        applyName(group);

        // بعد اكتمال الصف لا قبله: الصفُّ القائم مُدارٌ الآن، وأيُّ استعلامٍ يسبقه
        // يُجري flush تلقائياً فيكتبه كما هو في تلك اللحظة - وباسمٍ لم يُشتق بعدُ
        // يعني عموداً NOT NULL فارغاً ورفضاً من القاعدة مكان رسالة التعارض
        rejectIfTeacherIsBusy(group);

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
            if (GroupSchedules.conflicts(group, other)) {
                throw new IllegalStateException(I18n.format("error.group.teacherBusy",
                        group.getTeacher().getName(),
                        WeekDays.describe(GroupSchedules.sharedDays(group, other)),
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
            group.setName(GroupSchedules.compose(group));
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

        try {
            courseGroupRepository.deleteById(groupId);
            courseGroupRepository.flush();
        } catch (DataIntegrityViolationException error) {
            // مجموعةٌ لها حصص أو مشتركون لا تُحذف: حذفُها يمحو حضوراً وحركاتٍ أُقفلت
            // خزينةُ أيامها. والرسالة تقول ذلك بدل قيدٍ يصل بجملةٍ فيها اسم الجدول
            throw new IllegalStateException(I18n.get("group.deleteBlocked"), error);
        }
        auditService.record(AuditAction.GROUP_DELETED, groupId, name.orElse(null));
    }

}
