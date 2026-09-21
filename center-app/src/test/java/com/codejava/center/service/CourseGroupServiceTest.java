package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.service.dto.CourseGroupDraft;
import com.codejava.center.util.I18n;
import com.codejava.center.TestActor;
import com.codejava.center.util.WeekDays;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * قيود حفظ المجموعة: البيانات الواجبة، تعارض مواعيد المعلم، والاسم المشتق.
 *
 * <p>التعارض يُختبَر عبر الخدمة لا عبر {@link GroupSchedule} وحدها، لأن نصف القرار هو
 * <b>أي المجموعات تُقارَن</b>: مجموعات المعلم نفسه دون الصف الجاري تعديله.</p>
 */
@DataJpaTest
@Import({CourseGroupService.class, AuditService.class, TestActor.class, SecurityConfig.class})
class CourseGroupServiceTest {

    @Autowired private CourseGroupService courseGroupService;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private TestEntityManager entityManager;

    private Teacher teacher;

    @BeforeEach
    void setUp() {
        teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("أ/ محمد").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());
    }

    @Test
    void rejectsAnotherGroupForTheSameTeacherAtAnOverlappingTime() {
        courseGroupService.saveGroup(draft(teacher, SchoolLevel.PREP1,
                Set.of(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), 16, 18));

        CourseGroupDraft clashing = draft(teacher, SchoolLevel.PREP2, Set.of(DayOfWeek.TUESDAY), 17, 19);

        assertThatThrownBy(() -> courseGroupService.saveGroup(clashing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(teacher.getName())
                .hasMessageContaining(WeekDays.displayName(DayOfWeek.TUESDAY));
    }

    /** قاعتان تعملان بالتوازي هو الوضع الطبيعي؛ القيد على المعلم لا على الساعة */
    @Test
    void allowsTheSameTimeForAnotherTeacher() {
        Teacher other = teacherRepository.saveAndFlush(Teacher.builder()
                .name("أ/ أحمد").subject("علوم")
                .commissionType("FIXED_AMOUNT").commissionValue(new BigDecimal("100.00"))
                .build());

        courseGroupService.saveGroup(draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        assertThatCode(() -> courseGroupService.saveGroup(
                draft(other, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18)))
                .doesNotThrowAnyException();
    }

    /**
     * تعديل مجموعة قائمة يقارنها بنفسها، فبلا استثناء الصف الجاري تعديله
     * ما كان تغيير سعرها ممكناً أبداً.
     */
    @Test
    void editingAGroupDoesNotConflictWithItself() {
        CourseGroup saved = courseGroupService.saveGroup(
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        CourseGroupDraft repriced = repriced(editing(saved.getId(),
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18)),
                new BigDecimal("75.00"));

        assertThatCode(() -> courseGroupService.saveGroup(repriced)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAGroupWithoutSchoolLevel() {
        CourseGroupDraft noLevel = draft(teacher, null, Set.of(DayOfWeek.SATURDAY), 16, 18);

        assertThatThrownBy(() -> courseGroupService.saveGroup(noLevel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.levelRequired"));
    }

    @Test
    void rejectsAGroupWithoutDays() {
        CourseGroupDraft noDays = draft(teacher, SchoolLevel.PREP1, Set.of(), 16, 18);

        assertThatThrownBy(() -> courseGroupService.saveGroup(noDays))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.daysRequired"));
    }

    @Test
    void rejectsAnEndTimeBeforeTheStartTime() {
        CourseGroupDraft reversed = draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 18, 16);

        assertThatThrownBy(() -> courseGroupService.saveGroup(reversed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.endBeforeStart"));
    }

    @Test
    void buildsTheNameFromLevelTeacherDaysAndTime() {
        CourseGroup saved = courseGroupService.saveGroup(
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        assertThat(saved.getName()).isEqualTo(GroupSchedules.compose(SchoolLevel.PREP1,
                teacher.getName(), Set.of(DayOfWeek.SATURDAY), LocalTime.of(16, 0)));
    }

    /** الاسم المشتق يتبع الموعد: نقل المجموعة إلى يوم آخر يجب ألا يترك اسماً يكذب */
    @Test
    void rebuildsTheNameWhenTheScheduleChanges() {
        CourseGroup saved = courseGroupService.saveGroup(
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        CourseGroup moved = courseGroupService.saveGroup(editing(saved.getId(),
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.MONDAY), 16, 18)));

        assertThat(moved.getName()).contains(WeekDays.displayName(DayOfWeek.MONDAY));
        assertThat(moved.getName()).doesNotContain(WeekDays.displayName(DayOfWeek.SATURDAY));
    }

    /**
     * التعديل يعيد المجموعة ومعلمها محمَّلاً.
     *
     * <p>ما يأتي من شاشة كيان منفصل، و{@code save} عليه {@code merge}: يُرجع نسخة مُدارة
     * أخرى معلمُها وكيل كسول. وبإغلاق المعاملة كان جدول المجموعات يسقط بـ
     * {@code LazyInitializationException} فور نجاح التعديل - وهو ما لا يظهر في اختبار
     * تبقى جلسته مفتوحة، ولذلك يُفرَّغ سياق الإدامة هنا صراحةً قبل الاستدعاء.</p>
     */
    @Test
    void returnsTheGroupWithItsTeacherLoadedAfterAnUpdate() {
        CourseGroup saved = courseGroupService.saveGroup(
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        entityManager.flush();
        entityManager.clear(); // سياق إدامة فارغ، كما يبدأ كل طلب وكل ضغطة زر

        CourseGroup updated = courseGroupService.saveGroup(repriced(editing(saved.getId(),
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18)),
                new BigDecimal("60.00")));

        assertThat(Hibernate.isInitialized(updated.getTeacher()))
                .as("معلم المجموعة العائدة يجب أن يكون محمَّلاً قبل إغلاق المعاملة")
                .isTrue();
        assertThat(updated.getTeacher().getName()).isEqualTo(teacher.getName());
    }

    @Test
    void keepsACustomNameUntouched() {
        CourseGroupDraft custom = named(
                draft(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18),
                "مجموعة المتفوقين");

        assertThat(courseGroupService.saveGroup(custom).getName()).isEqualTo("مجموعة المتفوقين");
    }

    private CourseGroupDraft draft(Teacher owner, SchoolLevel level, Set<DayOfWeek> days,
                                   int startHour, int endHour) {
        return new CourseGroupDraft(null, owner.getId(), null, true, level, 20,
                new BigDecimal("50.00"), days, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    /** المسودة نفسها موجَّهةً إلى مجموعة قائمة - وهو ما يفعله زر التعديل */
    private static CourseGroupDraft editing(Long id, CourseGroupDraft source) {
        return new CourseGroupDraft(id, source.teacherId(), source.name(), source.autoName(),
                source.schoolLevel(), source.maxCapacity(), source.sessionPrice(),
                source.meetingDays(), source.startTime(), source.endTime());
    }

    private static CourseGroupDraft repriced(CourseGroupDraft source, BigDecimal price) {
        return new CourseGroupDraft(source.id(), source.teacherId(), source.name(), source.autoName(),
                source.schoolLevel(), source.maxCapacity(), price,
                source.meetingDays(), source.startTime(), source.endTime());
    }

    /** اسمٌ يكتبه المستخدم يعني إيقاف الاشتقاق: الحقلان يتحركان معاً دائماً */
    private static CourseGroupDraft named(CourseGroupDraft source, String name) {
        return new CourseGroupDraft(source.id(), source.teacherId(), name, false,
                source.schoolLevel(), source.maxCapacity(), source.sessionPrice(),
                source.meetingDays(), source.startTime(), source.endTime());
    }
}
