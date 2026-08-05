package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * حصة مفتوحة اقترب موعد انتهائها - أو مضى - وما زالت مفتوحة.
 *
 * <p>هذا هو التنبيه الموجَّه إلى موظف مشغول عن الشاشة. إغلاق الحصة ليس ترتيباً: ما دامت
 * مفتوحة لا تستطيع المجموعة فتح حصة غداً، ولا تدخل مستحقات معلمها قائمة الصرف. والموظف
 * الذي ينسى الإغلاق لا يكتشف ذلك بنفسه أبداً، لأن كل شيء آخر يبدو طبيعياً.</p>
 *
 * <p><b>بلا حدّ أعلى للنافذة عن قصد.</b> تبدأ عند {@code الانتهاء − الحدّ} وتستمر ما
 * دامت الحصة مفتوحة، فتغطّي "على وشك الانتهاء" و"انتهت من ساعة ولم تُغلق" معاً. ولا
 * تتكرر رغم ذلك: مفتاح الواقعة هو الحصة نفسها بتاريخها، فتنبيه واحد لكل حصة.</p>
 *
 * <p>وذلك مقصود: التكرار كل خمس دقائق يعلّم المستخدم إغلاق البطاقات دون قراءتها. من
 * تجاهل التذكير يلتقطه {@code SESSION_LEFT_OPEN} في فحص الصباح التالي بدرجة حرجة.</p>
 *
 * <p>وقت الانتهاء يأتي من المجموعة لا من الحصة - الحصة تحمل تاريخاً بلا ساعات - ومجموعةٌ
 * بلا وقت انتهاء مضبوط تُتخطّى بدل أن يُخمَّن لها وقت.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionEndingSoonDetector implements AlertDetector {

    private final SessionRepository sessionRepository;

    @Override
    public AlertType type() {
        return AlertType.SESSION_ENDING_SOON;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        LocalDateTime now = LocalDateTime.now();
        int minutesBefore = Math.max(1, rule.thresholdOrDefault());

        List<AlertDraft> drafts = new ArrayList<>();

        for (Session session : sessionRepository.findAllActive()) {
            LocalTime endTime = session.getGroup().getEndTime();
            if (endTime == null) {
                continue;
            }

            LocalDateTime end = session.getSessionDate().atTime(endTime);
            if (now.isBefore(end.minusMinutes(minutesBefore))) {
                continue;
            }

            drafts.add(AlertDraft.occurrence(session.getId(), session.getGroup().getName(),
                    session.getSessionDate().toString(),
                    session.getGroup().getName(), Times.clock(endTime)));
        }

        return drafts;
    }
}
