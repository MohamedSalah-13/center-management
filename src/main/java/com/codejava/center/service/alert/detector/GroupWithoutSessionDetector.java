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
 * مجموعات قائمة لم تُعقد لها حصة منذ مدة.
 *
 * <p>مجموعة متوقفة بلا قرار تظلّ تشغل حصةً من سعة السنتر وتظهر في كل قائمة اختيار،
 * وطلابها مشتركون فيها اسمياً فلا يظهرون في أي تقرير غياب - لأن الغياب لا يُحسب إلا
 * على حصص منعقدة. أي أن التوقّف الصامت يُخفي نفسه من كل التقارير الأخرى، وهذا هو
 * التنبيه الوحيد الذي يراه.</p>
 */
@Component
@RequiredArgsConstructor
public class GroupWithoutSessionDetector implements AlertDetector {

    private final SessionRepository sessionRepository;

    @Override
    public AlertType type() {
        return AlertType.GROUP_WITHOUT_SESSION;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        int days = Math.max(1, rule.windowDaysOrDefault());

        return sessionRepository.findGroupsWithoutSessionSince(LocalDate.now().minusDays(days)).stream()
                .map(group -> AlertDraft.internal(group.getId(), group.getName(),
                        group.getName(), String.valueOf(days)))
                .toList();
    }
}
