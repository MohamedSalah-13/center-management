package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.service.dto.DayBriefing;
import com.codejava.center.service.dto.DayScheduleRow;
import com.codejava.center.util.I18n;
import com.codejava.center.util.Moments;
import com.codejava.center.util.WeekDays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * جدول حصص يوم واحد: ما الذي يعمل اليوم، وما الذي فُتح منه فعلاً.
 *
 * <p>سؤالان يجيب عنهما موضعان مختلفان في النظام، ولا معنى لأحدهما بلا الآخر: الجدول
 * الأسبوعي في {@code CourseGroup} يقول متى <b>ينبغي</b> أن تجتمع المجموعة، و{@code Session}
 * يقول ما فُتح <b>بالفعل</b>. الموظف على المكتب يحتاج الفرق بينهما: مجموعة موعدها الرابعة
 * ولم تُفتح لها حصة والساعة الخامسة هي بالضبط ما لا يظهر في أي شاشة أخرى.</p>
 *
 * <p>والصفوف تشمل حصصاً فُتحت لمجموعة لا يجتمع موعدها اليوم - حصة تعويضية - وإلا كان
 * "جدول اليوم" يخفي حصةً جارية الآن في قاعة من قاعات السنتر.</p>
 *
 * <p>خدمة قائمة بذاتها لا دالة في {@code SessionService}: هذه قراءة تجمع أربعة مستودعات
 * ولا تكتب شيئاً، وتلك تفتح الحصص وتغلقها.</p>
 */
@Service
@RequiredArgsConstructor
public class DayScheduleService {

    private final CourseGroupRepository courseGroupRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;

    /**
     * جدول اليوم مرتّباً بموعد الانعقاد: أقرب المواعيد أولاً، وما لا موعد له في الذيل.
     *
     * <p>التصفية على يوم الأسبوع تجري في الذاكرة لا في SQL: {@code meetingDays} يُخزَّن
     * نصّاً واحداً عبر {@code AttributeConverter}، فمطابقته في الاستعلام تصير
     * {@code LIKE '%SATURDAY%'} - وهي تطابق ما لا يُقصد ولا يمسكها اختبار. ومجموعات
     * السنتر عشرات لا آلاف.</p>
     */
    @Transactional(readOnly = true)
    public List<DayScheduleRow> getSchedule(LocalDate date) {
        Map<Long, Session> sessionsByGroup = sessionsOf(date);
        List<CourseGroup> due = dueOn(date, sessionsByGroup);

        List<DayScheduleRow> rows = new ArrayList<>(due.size());
        for (CourseGroup group : due) {
            rows.add(describe(group, sessionsByGroup.get(group.getId()), date));
        }
        return rows;
    }

    /**
     * الموجز نفسه في أربعة أرقام، ومعها أقرب موعد لم يحن بعد.
     *
     * <p>يمرّ من {@link #dueOn} و{@link #stateOf} كما يمرّ الجدول، فلا يمكن أن يقول
     * "خمس مجموعات اليوم" بينما تعرض الشاشة ستّة صفوف: قاعدة "أي المجموعات يخصّها هذا
     * اليوم" مكتوبة مرة واحدة. ولا يمرّ من {@link #describe}، لأن ذاك يسأل قاعدة
     * البيانات مرتين عن كل مجموعة (الحاضرون والمشتركون) - وهي أرقام لا تظهر في الموجز
     * أصلاً، ولا يُدفع ثمنها في لحظة فتح البرنامج.</p>
     *
     * @param asOf اللحظة التي يُقاس عليها "لم يحن بعد"؛ ما مضى موعده لا يُعدّ قادماً،
     *             وهو ما يجعل الموجز يقول الحقيقة عند فتح البرنامج ظهراً وعصراً
     */
    @Transactional(readOnly = true)
    public DayBriefing brief(LocalDate date, LocalTime asOf) {
        Map<Long, Session> sessionsByGroup = sessionsOf(date);
        List<CourseGroup> due = dueOn(date, sessionsByGroup);

        long open = 0;
        long notOpened = 0;
        long closed = 0;
        CourseGroup next = null;

        for (CourseGroup group : due) {
            switch (stateOf(sessionsByGroup.get(group.getId()))) {
                case OPEN -> open++;
                case CLOSED -> closed++;
                case NOT_OPENED -> {
                    notOpened++;
                    // القائمة مرتّبة بالموعد، فأول قادم فيها هو أقربها - ومن لا موعد
                    // له يقع في ذيلها ولا يجتاز شرط الموعد أصلاً
                    if (next == null && isUpcoming(group, date, asOf)) {
                        next = group;
                    }
                }
            }
        }

        return new DayBriefing(due.size(), open, notOpened, closed,
                next == null ? null : next.getName(),
                next == null ? null : next.getStartTime());
    }

    /**
     * مجموعات اليوم مرتّبةً بموعد الانعقاد: أقرب المواعيد أولاً، وما لا موعد له في الذيل.
     *
     * <p>وتشمل مجموعةً لا يجتمع موعدها اليوم ولها حصة مفتوحة - حصة تعويضية - وإلا كان
     * "جدول اليوم" يخفي حصةً جارية الآن في قاعة من قاعات السنتر.</p>
     */
    private List<CourseGroup> dueOn(LocalDate date, Map<Long, Session> sessionsByGroup) {
        return courseGroupRepository.findAll().stream()
                .filter(group -> meetsOn(group, date) || sessionsByGroup.containsKey(group.getId()))
                .sorted(Comparator
                        .comparing(CourseGroup::getStartTime,
                                Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
                        .thenComparing(CourseGroup::getName))
                .toList();
    }

    /** مجموعة موعدها اليوم ولم يحن بعد. الموعد الفارغ ليس قادماً: لا شيء يُقال عنه */
    private boolean isUpcoming(CourseGroup group, LocalDate date, LocalTime asOf) {
        return meetsOn(group, date)
                && group.getStartTime() != null
                && !group.getStartTime().isBefore(asOf);
    }

    /**
     * حصص اليوم مفهرسةً بمجموعتها.
     *
     * <p>المفتاح واحد لأن المجموعة لا يكون لها حصتان في اليوم الواحد
     * ({@code SessionService.openSession})، لكن قاعدة قائمة قد تحمل صفّين من قبل ذلك
     * القيد - وحينها تُقدَّم المفتوحة: هي ما يجري الآن وما يُسجَّل عليه الحضور.</p>
     */
    private Map<Long, Session> sessionsOf(LocalDate date) {
        Map<Long, Session> byGroup = new HashMap<>();
        for (Session session : sessionRepository.findFiltered(date, null)) {
            byGroup.merge(session.getGroup().getId(), session,
                    (first, second) -> first.isActive() ? first : second);
        }
        return byGroup;
    }

    private boolean meetsOn(CourseGroup group, LocalDate date) {
        return group.getMeetingDays() != null && group.getMeetingDays().contains(date.getDayOfWeek());
    }

    /** ما جرى للمجموعة اليوم، من حصتها إن وُجدت. الجدول والموجز يقرآنه من هنا وحده */
    private DayScheduleRow.State stateOf(Session session) {
        if (session == null) {
            return DayScheduleRow.State.NOT_OPENED;
        }
        return session.isActive() ? DayScheduleRow.State.OPEN : DayScheduleRow.State.CLOSED;
    }

    private DayScheduleRow describe(CourseGroup group, Session session, LocalDate date) {
        DayScheduleRow.State state = stateOf(session);

        // عدد المشتركين يُقرأ بتاريخ اليوم المعروض لا بعدد الأعضاء الآن: طالب خرج
        // الأسبوع الماضي لم يكن غائباً عن حصة الثلاثاء التي سبقت خروجه
        long enrolled = studentGroupRepository.countEnrolledOn(group.getId(), date);
        String attended = session == null
                ? I18n.get("common.empty") // لم تُفتح: لا أحد عُدّ، وهو غير أن أحداً لم يحضر
                : String.valueOf(attendanceRepository.countBySession(session));

        return new DayScheduleRow(
                group.getName(),
                group.getTeacher().getName(),
                group.getSchoolLevel() == null
                        ? I18n.get("common.none") : group.getSchoolLevel().getDisplayName(),
                // مجموعة لا يجتمع موعدها اليوم ولها حصة: الخانة الفارغة هي ما يقول
                // إن هذه حصة خارج الجدول، لا موعداً منسيّاً
                meetsOn(group, date)
                        ? WeekDays.describeRange(group.getStartTime(), group.getEndTime())
                        : I18n.get("common.empty"),
                statusLabel(state),
                Moments.describe(session == null ? null : session.getStartedAt(), date),
                Moments.describe(session == null ? null : session.getEndedAt(), date),
                I18n.format("daySchedule.attendanceOf", attended, enrolled),
                state);
    }

    private String statusLabel(DayScheduleRow.State state) {
        return switch (state) {
            case NOT_OPENED -> I18n.get("daySchedule.status.notOpened");
            case OPEN -> I18n.get("daySchedule.status.open");
            case CLOSED -> I18n.get("daySchedule.status.closed");
        };
    }
}
