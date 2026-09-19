package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.service.dto.DayBriefing;
import com.codejava.center.service.dto.DayScheduleRow;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * جدول اليوم: الجدول الأسبوعي مطبَّقاً على يوم، ومعه ما فُتح فعلاً.
 *
 * <p>ما يستحق الاختبار هنا هو <b>أي الصفوف تظهر</b>: مجموعة موعدها اليوم ولم تُفتح لها
 * حصة هي بيت القصيد من الشاشة، وحصة فُتحت لمجموعة لا موعد لها اليوم لا يجوز أن تختفي -
 * وكلتاهما تغيب بصمت لو بُنيت القائمة من جهة واحدة.</p>
 */
@DataJpaTest
@Import({DayScheduleService.class, SessionService.class, AuditService.class,
        com.codejava.center.util.UserSession.class, SecurityConfig.class})
class DayScheduleServiceTest {

    @Autowired private DayScheduleService dayScheduleService;
    @Autowired private SessionService sessionService;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private SessionRepository sessionRepository;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    private Teacher teacher;

    @BeforeEach
    void setUp() {
        teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("أ/ محمد").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());
    }

    /** مجموعة موعدها اليوم ولم تُفتح لها حصة: الصف الذي لا تعرضه أي شاشة أخرى */
    @Test
    void showsAGroupDueTodayThatHasNoSessionYet() {
        CourseGroup due = group("مجموعة الاثنين", Set.of(DayOfWeek.MONDAY), LocalTime.of(16, 0));

        List<DayScheduleRow> schedule = dayScheduleService.getSchedule(MONDAY);

        assertThat(schedule).singleElement().satisfies(row -> {
            assertThat(row.getGroupName()).isEqualTo(due.getName());
            assertThat(row.getTeacherName()).isEqualTo(teacher.getName());
            assertThat(row.getState()).isEqualTo(DayScheduleRow.State.NOT_OPENED);
            assertThat(row.isOpen()).isFalse();
            assertThat(row.getStartedAt()).isEqualTo(I18n.get("common.empty"));
            assertThat(row.getStatus()).isEqualTo(I18n.get("daySchedule.status.notOpened"));
        });
    }

    @Test
    void leavesOutGroupsThatDoNotMeetOnThatDay() {
        group("مجموعة الثلاثاء", Set.of(DayOfWeek.TUESDAY), LocalTime.of(16, 0));

        assertThat(dayScheduleService.getSchedule(MONDAY)).isEmpty();
    }

    /**
     * حصة تعويضية لمجموعة لا يجتمع موعدها اليوم تظهر رغم ذلك: بناء القائمة من جهة
     * الجدول وحده يُخفي حصةً جارية الآن في إحدى القاعات.
     */
    @Test
    void includesASessionOpenedOutsideTheGroupTimetable() {
        CourseGroup tuesdayGroup = group("مجموعة الثلاثاء", Set.of(DayOfWeek.TUESDAY), LocalTime.of(16, 0));
        openSessionOn(tuesdayGroup, MONDAY, true);

        assertThat(dayScheduleService.getSchedule(MONDAY)).singleElement().satisfies(row -> {
            assertThat(row.getGroupName()).isEqualTo(tuesdayGroup.getName());
            assertThat(row.isOpen()).isTrue();
            // خانة الموعد فارغة: هي ما يقول إن هذه حصة خارج جدول المجموعة
            assertThat(row.getScheduledTime()).isEqualTo(I18n.get("common.empty"));
        });
    }

    /** الحالات الثلاث كما يقرؤها الموظف: لم تُفتح، جارية الآن، انتهت */
    @Test
    void reportsTheThreeStatesOfADay() {
        CourseGroup waiting = group("بانتظار الفتح", Set.of(DayOfWeek.MONDAY), LocalTime.of(18, 0));
        CourseGroup running = group("جارية", Set.of(DayOfWeek.MONDAY), LocalTime.of(16, 0));
        CourseGroup finished = group("منتهية", Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0));

        openSessionOn(running, MONDAY, true);
        openSessionOn(finished, MONDAY, false);

        List<DayScheduleRow> schedule = dayScheduleService.getSchedule(MONDAY);

        // مرتّبة بموعد الانعقاد: أقرب المواعيد أولاً
        assertThat(schedule).extracting(DayScheduleRow::getGroupName)
                .containsExactly(finished.getName(), running.getName(), waiting.getName());
        assertThat(schedule).extracting(DayScheduleRow::getState)
                .containsExactly(DayScheduleRow.State.CLOSED, DayScheduleRow.State.OPEN,
                        DayScheduleRow.State.NOT_OPENED);
    }

    /**
     * الحضور من المشتركين، والمشتركون محسوبون بتاريخ اليوم المعروض: طالب خرج بعد ذلك
     * اليوم كان في المجموعة يومها، وعدُّه بالعضوية الحالية يجعل حصة الأمس تبدو أصغر.
     */
    @Test
    void countsAttendanceAgainstTheMembershipOfThatDay() {
        CourseGroup due = group("مجموعة الاثنين", Set.of(DayOfWeek.MONDAY), LocalTime.of(16, 0));
        enrol(due, "STU-DS01", MONDAY.minusMonths(1), null);          // مشترك مستمر
        enrol(due, "STU-DS02", MONDAY.minusMonths(1), MONDAY.plusDays(2)); // خرج بعد ذلك اليوم
        enrol(due, "STU-DS03", MONDAY.plusWeeks(1), null);            // انضم بعده

        openSessionOn(due, MONDAY, true);

        assertThat(dayScheduleService.getSchedule(MONDAY)).singleElement()
                .satisfies(row -> assertThat(row.getAttendance())
                        .isEqualTo(I18n.format("daySchedule.attendanceOf", "0", 2L)));
    }

    /** يوم بلا موعد ولا حصة: قائمة فارغة، والشاشة هي التي تقول ذلك */
    @Test
    void returnsNothingForAnEmptyDay() {
        group("مجموعة الاثنين", Set.of(DayOfWeek.MONDAY), LocalTime.of(16, 0));

        assertThat(dayScheduleService.getSchedule(MONDAY.plusDays(4))).isEmpty();
    }

    /**
     * موجز البطاقة التي تظهر عند فتح البرنامج: نفس أرقام الشاشة، وأقرب موعد لم يحن بعد.
     *
     * <p>"أقرب موعد" هو ما يُخطئ بصمت: مجموعةٌ مضى موعدها ولم تُفتح تبقى في عدّ "لم
     * تُفتح" - وهي مشكلة قائمة - لكنها لا تصلح جواباً لسؤال "ما التالي".</p>
     */
    @Test
    void briefsTheDayWithItsCountsAndTheNextGroupDue() {
        CourseGroup running = group("جارية", Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0));
        CourseGroup missed = group("فات موعدها", Set.of(DayOfWeek.MONDAY), LocalTime.of(15, 0));
        CourseGroup next = group("التالية", Set.of(DayOfWeek.MONDAY), LocalTime.of(18, 0));
        group("بعد التالية", Set.of(DayOfWeek.MONDAY), LocalTime.of(20, 0));
        group("مجموعة الثلاثاء", Set.of(DayOfWeek.TUESDAY), LocalTime.of(16, 0)); // ليست من اليوم

        openSessionOn(running, MONDAY, true);

        DayBriefing brief = dayScheduleService.brief(MONDAY, LocalTime.of(16, 30));

        assertThat(brief.total()).isEqualTo(4);
        assertThat(brief.open()).isEqualTo(1);
        assertThat(brief.notOpened()).isEqualTo(3); // منها التي فات موعدها
        assertThat(brief.closed()).isZero();
        assertThat(brief.hasNext()).isTrue();
        assertThat(brief.nextGroupName()).isEqualTo(next.getName());
        assertThat(brief.nextStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(missed.getName()).isNotEqualTo(brief.nextGroupName());
    }

    /** آخر مواعيد اليوم مضى: لا "تالية" تُخترع، والبطاقة تُسقط سطرها */
    @Test
    void reportsNoNextGroupOnceTheLastSlotHasPassed() {
        CourseGroup last = group("آخر مجموعة", Set.of(DayOfWeek.MONDAY), LocalTime.of(18, 0));
        openSessionOn(last, MONDAY, false);

        DayBriefing brief = dayScheduleService.brief(MONDAY, LocalTime.of(21, 0));

        assertThat(brief.closed()).isEqualTo(1);
        assertThat(brief.hasNext()).isFalse();
        assertThat(brief.nextStartTime()).isNull();
    }

    /** يوم إجازة: الموجز يقول "لا شيء" صراحةً بدل أربعة أصفار تُقرأ على أنها عطل */
    @Test
    void reportsAnEmptyDayAsEmpty() {
        group("مجموعة الاثنين", Set.of(DayOfWeek.MONDAY), LocalTime.of(16, 0));

        assertThat(dayScheduleService.brief(MONDAY.plusDays(4), LocalTime.of(9, 0)).isEmpty()).isTrue();
    }

    private CourseGroup group(String name, Set<DayOfWeek> days, LocalTime start) {
        return courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name(name)
                .autoName(false)
                .teacher(teacher)
                .meetingDays(days)
                .startTime(start)
                .endTime(start.plusHours(2))
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("40.00"))
                .build());
    }

    /** الحصة تُكتب مباشرةً لأن الخدمة تختم الوقت بساعة اليوم، والاختبار على يوم ثابت */
    private void openSessionOn(CourseGroup group, LocalDate date, boolean active) {
        sessionRepository.saveAndFlush(Session.builder()
                .group(group)
                .sessionDate(date)
                .startedAt(date.atTime(16, 5))
                .endedAt(active ? null : date.atTime(18, 10))
                .isActive(active)
                .isPaidOut(false)
                .build());
    }

    private void enrol(CourseGroup group, String barcode, LocalDate joined, LocalDate left) {
        Student student = studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name("طالب " + barcode).isActive(true).build());

        studentGroupRepository.saveAndFlush(StudentGroup.builder()
                .student(student).group(group)
                .joinDate(joined).leaveDate(left)
                .isActive(left == null)
                .build());
    }
}
