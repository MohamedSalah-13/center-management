package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.util.I18n;
import com.codejava.center.util.WeekDays;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قرار "هل المعلم مشغول في هذا الموعد؟" واسم المجموعة المشتق منه.
 *
 * <p>بلا Spring ولا قاعدة بيانات - نفس منطق {@code BackupScheduleTest}: هذا حساب خالص،
 * وخطأ فيه يعني معلماً محجوزاً في قاعتين أو مجموعتين لا تُحفظان بلا سبب ظاهر.</p>
 */
class GroupScheduleTest {

    @Test
    void detectsOverlapOnASharedDay() {
        CourseGroup first = group(Set.of(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), 16, 18);
        CourseGroup second = group(Set.of(DayOfWeek.TUESDAY), 17, 19);

        assertThat(GroupSchedule.conflicts(first, second)).isTrue();
        assertThat(GroupSchedule.sharedDays(first, second)).containsExactly(DayOfWeek.TUESDAY);
    }

    @Test
    void allowsTheSameHourOnDifferentDays() {
        CourseGroup saturday = group(Set.of(DayOfWeek.SATURDAY), 16, 18);
        CourseGroup sunday = group(Set.of(DayOfWeek.SUNDAY), 16, 18);

        assertThat(GroupSchedule.conflicts(saturday, sunday)).isFalse();
    }

    /**
     * التلامس ليس تداخلاً: مجموعة تنتهي السادسة وأخرى تبدأ السادسة هي الطريقة التي
     * يُبنى بها جدول السنتر، ومنعها كان سيمنع نصف المواعيد الحقيقية.
     */
    @Test
    void allowsBackToBackGroups() {
        CourseGroup earlier = group(Set.of(DayOfWeek.SATURDAY), 16, 18);
        CourseGroup later = group(Set.of(DayOfWeek.SATURDAY), 18, 20);

        assertThat(GroupSchedule.conflicts(earlier, later)).isFalse();
    }

    @Test
    void detectsFullContainment() {
        CourseGroup wide = group(Set.of(DayOfWeek.MONDAY), 15, 20);
        CourseGroup narrow = group(Set.of(DayOfWeek.MONDAY), 16, 17);

        assertThat(GroupSchedule.conflicts(wide, narrow)).isTrue();
        assertThat(GroupSchedule.conflicts(narrow, wide)).isTrue();
    }

    /** مجموعة أُنشئت قبل هذه الميزة بلا موعد: لا يُحكم عليها بتعارض فتُعطَّل بيانات قائمة */
    @Test
    void groupWithoutScheduleNeverConflicts() {
        CourseGroup scheduled = group(Set.of(DayOfWeek.SATURDAY), 16, 18);
        CourseGroup legacy = CourseGroup.builder().name("قديمة").build();

        assertThat(GroupSchedule.conflicts(scheduled, legacy)).isFalse();
        assertThat(GroupSchedule.hasSchedule(legacy)).isFalse();
    }

    @Test
    void composesNameFromLevelTeacherDaysAndStartTime() {
        String name = GroupSchedule.compose(SchoolLevel.PREP1, "أ/ محمد",
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), LocalTime.of(16, 0));

        assertThat(name).isEqualTo(I18n.format("group.autoName",
                SchoolLevel.PREP1.getDisplayName(), "أ/ محمد",
                WeekDays.describe(Set.of(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)),
                WeekDays.describeTime(LocalTime.of(16, 0))));
    }

    /** الاسم يُخزَّن في عمود بطول محدود؛ التجاوز كان سيرفضه MySQL عند الحفظ */
    @Test
    void composedNameNeverExceedsTheColumnLength() {
        String name = GroupSchedule.compose(SchoolLevel.SEC3, "أ".repeat(200),
                Set.of(DayOfWeek.values()), LocalTime.of(9, 30));

        assertThat(name.length()).isLessThanOrEqualTo(GroupSchedule.MAX_NAME_LENGTH);
    }

    private CourseGroup group(Set<DayOfWeek> days, int startHour, int endHour) {
        return CourseGroup.builder()
                .name("مجموعة")
                .teacher(Teacher.builder().name("أ/ محمد").build())
                .schoolLevel(SchoolLevel.PREP1)
                .meetingDays(days)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .build();
    }
}
