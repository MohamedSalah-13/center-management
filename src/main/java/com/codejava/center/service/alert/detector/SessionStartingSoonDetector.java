package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * اقترب موعد مجموعة اليوم ولم تُفتح لها حصة بعد.
 *
 * <p>الحصة لا تُفتح من تلقاء نفسها: ما دامت مغلقة تردّ بوابة الباركود كل طالب يمرّ
 * عليها، فيقف الطابور عند الباب بينما الموظف يظنّ الجهاز معطّلاً. هذا التنبيه يسبق
 * ذلك بدقائق.</p>
 *
 * <p><b>ويصمت فور فتح الحصة.</b> شرط "لا حصة مفتوحة لهذه المجموعة" هو نصف الميزة: بدونه
 * يصل التذكير لموظف فتح الحصة قبل ربع ساعة، فيتعلّم أن التنبيه لا علاقة له بما فعل.</p>
 *
 * <p>النافذة {@code [البداية − الحدّ، البداية)} لا ما بعدها: بعد بدء الموعد لم يعد
 * "يقترب"، ونصٌّ يقول "تبدأ الساعة الرابعة" في الرابعة والنصف كذبٌ صريح. المجموعة التي
 * فات موعدها ولم تُفتح حالٌ أخرى يقيسها الفحص اليومي.</p>
 *
 * <p>المجموعات تُصفّى في الذاكرة لا في SQL: {@code meetingDays} عمود نصّي محوَّل
 * ({@code MeetingDaysConverter}) فلا يُستعلم عنه، والمجموعات في السنتر عشرات لا آلاف.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionStartingSoonDetector implements AlertDetector {

    private final CourseGroupRepository courseGroupRepository;
    private final SessionRepository sessionRepository;

    @Override
    public AlertType type() {
        return AlertType.SESSION_STARTING_SOON;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        int minutesBefore = Math.max(1, rule.thresholdOrDefault());

        Set<Long> alreadyOpen = sessionRepository.findAllActive().stream()
                .map(Session::getGroup)
                .map(CourseGroup::getId)
                .collect(Collectors.toSet());

        List<AlertDraft> drafts = new ArrayList<>();

        for (CourseGroup group : courseGroupRepository.findAll()) {
            LocalTime startTime = group.getStartTime();

            // مجموعة بلا موعد مضبوط لا يُستنتج لها شيء - وهي حال المجموعات التي
            // أُنشئت قبل إضافة الجدول الأسبوعي
            if (startTime == null || !group.getMeetingDays().contains(today.getDayOfWeek())) {
                continue;
            }
            if (alreadyOpen.contains(group.getId())) {
                continue;
            }

            LocalDateTime start = today.atTime(startTime);
            if (now.isBefore(start.minusMinutes(minutesBefore)) || !now.isBefore(start)) {
                continue;
            }

            // مفتاح الواقعة: هذه المجموعة في هذا الموعد بالذات. يخرج هو نفسه على كل
            // جهاز في السنتر، وهو شرط أن يمنع القيد الفريد تكرار التنبيه بينها
            drafts.add(AlertDraft.occurrence(group.getId(), group.getName(),
                    today + "T" + startTime,
                    group.getName(), Times.clock(startTime)));
        }

        return drafts;
    }
}
