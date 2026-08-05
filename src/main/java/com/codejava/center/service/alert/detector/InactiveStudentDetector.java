package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * طلاب مشتركون انقطعوا عن الحضور تماماً منذ مدة.
 *
 * <p>غير تنبيه الغياب: ذاك يقيس داخل مجموعة وفترة تقرير ويصلح لمن يحضر ويغيب، وهذا
 * يقول إن الطالب اختفى من السنتر كله. الفرق عملي لا لفظي - الأول يستدعي مكالمة
 * متابعة، والثاني يستدعي سؤالاً عمّا إذا كان الطالب ما زال مشتركاً أصلاً.</p>
 */
@Component
@RequiredArgsConstructor
public class InactiveStudentDetector implements AlertDetector {

    private final StudentRepository studentRepository;

    @Override
    public AlertType type() {
        return AlertType.STUDENT_INACTIVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        int days = Math.max(1, rule.windowDaysOrDefault());
        List<Student> inactive = studentRepository.findInactiveSince(LocalDate.now().minusDays(days));

        return inactive.stream()
                .map(student -> AlertDraft.forStudent(student.getId(), student.getName(),
                        student.getParentPhone(), student.getName(), String.valueOf(days)))
                .toList();
    }
}
