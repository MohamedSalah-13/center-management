package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * معلّمون تراكمت لهم حصص مغلقة لم تُصرَف مستحقاتها.
 *
 * <p>شرطان بينهما "أو" لا "و": إمّا عددٌ تجاوز الحدّ، وإمّا حصة واحدة قديمة تجاوزت
 * الفترة. المعلّم صاحب الحصة الواحدة منذ شهر هو الأولى بالتنبيه لا العكس - قلّة العدد
 * تعني أن أحداً لا ينظر في مستحقاته أصلاً.</p>
 *
 * <p>التجميع في الذاكرة لا في SQL: {@code findPayableSessions} تجلب المعلّم مربوطاً
 * أصلاً، والحصص غير المصروفة عشراتٌ لا آلاف - استعلام تجميع ثانٍ لا يشتري شيئاً
 * ويضيف صيغةً أخرى للمعنى نفسه، فتفترقان يوم يتغيّر تعريف "قابلة للصرف".</p>
 */
@Component
@RequiredArgsConstructor
public class TeacherDuesDetector implements AlertDetector {

    private final SessionRepository sessionRepository;

    @Override
    public AlertType type() {
        return AlertType.TEACHER_DUES_PENDING;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        int minimumSessions = Math.max(1, rule.thresholdOrDefault());
        LocalDate tooOld = LocalDate.now().minusDays(Math.max(1, rule.windowDaysOrDefault()));

        Map<Long, Pending> byTeacher = new LinkedHashMap<>();

        for (Session session : sessionRepository.findPayableSessions()) {
            Teacher teacher = session.getGroup().getTeacher();
            Pending pending = byTeacher.computeIfAbsent(teacher.getId(),
                    id -> new Pending(teacher.getName()));

            pending.count++;
            pending.hasOldSession |= session.getSessionDate().isBefore(tooOld);
        }

        List<AlertDraft> drafts = new ArrayList<>();
        byTeacher.forEach((teacherId, pending) -> {
            if (pending.count >= minimumSessions || pending.hasOldSession) {
                drafts.add(AlertDraft.internal(teacherId, pending.name,
                        pending.name, String.valueOf(pending.count)));
            }
        });

        return drafts;
    }

    private static final class Pending {
        private final String name;
        private int count;
        private boolean hasOldSession;

        private Pending(String name) {
            this.name = name;
        }
    }
}
