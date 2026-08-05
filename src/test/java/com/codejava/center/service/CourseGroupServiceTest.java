package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import com.codejava.center.util.WeekDays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
@Import({CourseGroupService.class, AuditService.class, UserSession.class, SecurityConfig.class})
class CourseGroupServiceTest {

    @Autowired private CourseGroupService courseGroupService;
    @Autowired private TeacherRepository teacherRepository;

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
        courseGroupService.saveGroup(group(teacher, SchoolLevel.PREP1,
                Set.of(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), 16, 18));

        CourseGroup clashing = group(teacher, SchoolLevel.PREP2, Set.of(DayOfWeek.TUESDAY), 17, 19);

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

        courseGroupService.saveGroup(group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        assertThatCode(() -> courseGroupService.saveGroup(
                group(other, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18)))
                .doesNotThrowAnyException();
    }

    /**
     * تعديل مجموعة قائمة يقارنها بنفسها، فبلا استثناء الصف الجاري تعديله
     * ما كان تغيير سعرها ممكناً أبداً.
     */
    @Test
    void editingAGroupDoesNotConflictWithItself() {
        CourseGroup saved = courseGroupService.saveGroup(
                group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        saved.setSessionPrice(new BigDecimal("75.00"));

        assertThatCode(() -> courseGroupService.saveGroup(saved)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAGroupWithoutSchoolLevel() {
        CourseGroup noLevel = group(teacher, null, Set.of(DayOfWeek.SATURDAY), 16, 18);

        assertThatThrownBy(() -> courseGroupService.saveGroup(noLevel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.levelRequired"));
    }

    @Test
    void rejectsAGroupWithoutDays() {
        CourseGroup noDays = group(teacher, SchoolLevel.PREP1, Set.of(), 16, 18);

        assertThatThrownBy(() -> courseGroupService.saveGroup(noDays))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.daysRequired"));
    }

    @Test
    void rejectsAnEndTimeBeforeTheStartTime() {
        CourseGroup reversed = group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 18, 16);

        assertThatThrownBy(() -> courseGroupService.saveGroup(reversed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.get("error.group.endBeforeStart"));
    }

    @Test
    void buildsTheNameFromLevelTeacherDaysAndTime() {
        CourseGroup saved = courseGroupService.saveGroup(
                group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        assertThat(saved.getName()).isEqualTo(GroupSchedule.compose(SchoolLevel.PREP1,
                teacher.getName(), Set.of(DayOfWeek.SATURDAY), LocalTime.of(16, 0)));
    }

    /** الاسم المشتق يتبع الموعد: نقل المجموعة إلى يوم آخر يجب ألا يترك اسماً يكذب */
    @Test
    void rebuildsTheNameWhenTheScheduleChanges() {
        CourseGroup saved = courseGroupService.saveGroup(
                group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18));

        saved.setMeetingDays(Set.of(DayOfWeek.MONDAY));
        CourseGroup moved = courseGroupService.saveGroup(saved);

        assertThat(moved.getName()).contains(WeekDays.displayName(DayOfWeek.MONDAY));
        assertThat(moved.getName()).doesNotContain(WeekDays.displayName(DayOfWeek.SATURDAY));
    }

    @Test
    void keepsACustomNameUntouched() {
        CourseGroup custom = group(teacher, SchoolLevel.PREP1, Set.of(DayOfWeek.SATURDAY), 16, 18);
        custom.setAutoName(false);
        custom.setName("مجموعة المتفوقين");

        assertThat(courseGroupService.saveGroup(custom).getName()).isEqualTo("مجموعة المتفوقين");
    }

    private CourseGroup group(Teacher owner, SchoolLevel level, Set<DayOfWeek> days,
                              int startHour, int endHour) {
        return CourseGroup.builder()
                .teacher(owner)
                .schoolLevel(level)
                .meetingDays(days)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("50.00"))
                .build();
    }
}
