package com.codejava.center.web.api;

import com.codejava.center.service.DayScheduleService;
import com.codejava.center.service.dto.DayBriefing;
import com.codejava.center.service.dto.DayScheduleRow;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * جدول اليوم: أيّ مجموعات تخصّه، وأيُّها فُتحت حصتها.
 *
 * <p>هو الشاشة التي يقرؤها من يفتح الحصص - الصفّان مرتبطان: من يرى أن أربع مجموعات
 * بلا حصة هو من يفتحها. ولذلك يصل مع ب-1 لا بعدها.</p>
 *
 * <p>والخدمة تجيب عن السؤال في شكلين من مدخلٍ واحد: صفٌّ لكل مجموعة، وأربعةُ أرقام
 * مع المجموعة التالية. وكلاهما يمرّ بـ {@code dueOn} و{@code stateOf} نفسيهما -
 * فملخّصٌ يقول خمساً بينما الجدول يعدّ ستاً يجعل القارئ لا يثق بواحدٍ منهما.</p>
 */
@RestController
@RequestMapping("/api/day-schedule")
@RequiredArgsConstructor
public class DayScheduleController {

    private final DayScheduleService dayScheduleService;

    /**
     * ساعة البرنامج: "المجموعة التالية" تُقاس من لحظةٍ، وتلك اللحظة تُحقن لا تُقرأ.
     */
    private final Clock clock;

    public record ScheduleRow(String groupName, String teacherName, String level,
                              String scheduledTime, String status, String startedAt,
                              String endedAt, String attendance, String state) {
    }

    public record Brief(int total, long open, long notOpened, long closed,
                        String nextGroupName, LocalTime nextStartTime) {
    }

    public record DayView(LocalDate date, Brief brief, List<ScheduleRow> rows) {
    }

    @GetMapping
    public DayView day(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;

        // لحظةُ القياس هي الآن لليوم الجاري، ومنتصفُ الليل لأيّ يوم آخر: "المجموعة
        // التالية" في يومٍ غير اليوم تُقاس من أوله، وإلا لم يكن لأحدها تالٍ أبداً
        LocalTime asOf = day.equals(LocalDate.now(clock))
                ? LocalTime.now(clock)
                : LocalTime.MIDNIGHT;

        return new DayView(day, brief(dayScheduleService.brief(day, asOf)),
                dayScheduleService.getSchedule(day).stream()
                        .map(DayScheduleController::row)
                        .toList());
    }

    private static Brief brief(DayBriefing briefing) {
        return new Brief(briefing.total(), briefing.open(), briefing.notOpened(),
                briefing.closed(), briefing.nextGroupName(), briefing.nextStartTime());
    }

    /**
     * الصفّ يصل بنصوصه كما بنتها الخدمة.
     *
     * <p>الأوقات والحالات مكتوبةٌ هناك بلغة الطلب، وإعادةُ بنائها في المتصفّح تعني
     * صياغتين لشيء واحد - والثانية هي التي تختلف عن الورقة المطبوعة يوماً ما.</p>
     */
    private static ScheduleRow row(DayScheduleRow row) {
        return new ScheduleRow(row.getGroupName(), row.getTeacherName(), row.getLevel(),
                row.getScheduledTime(), row.getStatus(), row.getStartedAt(), row.getEndedAt(),
                row.getAttendance(), row.getState().name());
    }
}
