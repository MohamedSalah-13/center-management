package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import com.codejava.center.service.dto.AttendanceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * طلاب تغيّبوا عن عدد من الحصص خلال الفترة.
 *
 * <p>يمرّ على المجموعات مجموعةً مجموعة لا على الطلاب: الغياب لا يُقاس إلا بنسبةٍ إلى
 * عدد الحصص المنعقدة في المجموعة نفسها. "غاب ثلاث مرات" لا معنى له قبل أن يُعرف أن
 * المجموعة عقدت أربع حصص أو عشرين.</p>
 *
 * <p>ومجموعة لم تعقد أي حصة في الفترة تُتخطّى: كل طلابها "غائبون" حسابياً بينما لم
 * يقع غياب أصلاً - وهي الحالة التي كانت ستملأ الصندوق بمئة اسم صباح كل عطلة.</p>
 */
@Component
@RequiredArgsConstructor
public class AbsenceDetector implements AlertDetector {

    private final CourseGroupRepository courseGroupRepository;
    private final SessionRepository sessionRepository;
    private final StudentRepository studentRepository;

    @Override
    public AlertType type() {
        return AlertType.ABSENCE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(rule.windowDaysOrDefault());
        int minimumAbsences = Math.max(1, rule.thresholdOrDefault());

        List<AlertDraft> drafts = new ArrayList<>();

        for (CourseGroup group : courseGroupRepository.findAll()) {
            long totalSessions = sessionRepository.countByGroupIdAndSessionDateBetween(
                    group.getId(), from, to);
            if (totalSessions == 0) {
                continue;
            }

            for (AttendanceSummary row : studentRepository.findGroupAttendance(
                    group.getId(), from, to)) {
                long absences = totalSessions - row.attended();
                if (absences < minimumAbsences) {
                    continue;
                }

                drafts.add(AlertDraft.forStudent(row.studentId(), row.studentName(), row.parentPhone(),
                        row.studentName(), String.valueOf(absences),
                        String.valueOf(totalSessions), group.getName()));
            }
        }

        return drafts;
    }
}
