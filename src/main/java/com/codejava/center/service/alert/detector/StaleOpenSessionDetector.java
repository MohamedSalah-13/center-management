package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * حصص نُسي إغلاقها بعد انقضاء يومها.
 *
 * <p>درجتها حرجة لأنها تُعطّل شيئين في آن: المجموعة لا تستطيع فتح حصة جديدة ما دامت
 * لها حصة مفتوحة، ومستحقات معلّمها عن هذه الحصة لا تدخل قائمة الصرف حتى تُغلق. الموظف
 * الذي يقف أمام رسالة "للمجموعة حصة مفتوحة بالفعل" في اليوم التالي لا يعرف عادةً من
 * أين يأتيها الحل، وهذا التنبيه هو الجواب قبل السؤال.</p>
 */
@Component
@RequiredArgsConstructor
public class StaleOpenSessionDetector implements AlertDetector {

    private final SessionRepository sessionRepository;

    @Override
    public AlertType type() {
        return AlertType.SESSION_LEFT_OPEN;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        // حصة اليوم مفتوحة أمرٌ طبيعي؛ المقصود ما بقي مفتوحاً بعد انقضاء يومه
        LocalDate before = LocalDate.now().minusDays(Math.max(1, rule.windowDaysOrDefault()) - 1L);

        return sessionRepository.findOpenSessionsBefore(before).stream()
                .map(session -> AlertDraft.internal(session.getId(), session.getGroup().getName(),
                        session.getGroup().getName(), session.getSessionDate().toString()))
                .toList();
    }
}
