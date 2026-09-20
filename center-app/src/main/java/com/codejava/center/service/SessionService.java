package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PersistenceErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final AuditService auditService;

    /**
     * فتح حصة جديدة لمجموعة.
     *
     * <p>يُسمح بعدة حصص مفتوحة في نفس الوقت (قاعات متوازية)، والقيدان:</p>
     * <ul>
     *   <li>المجموعة الواحدة لا يكون لها حصتان مفتوحتان معاً؛</li>
     *   <li>ولا حصتان بنفس المواصفات - نفس المجموعة ونفس اليوم - ولو أُغلقت الأولى.
     *       تكرار الحصة يخصم رسومها من الطالب مرتين ويُحتسب للمعلم يومان عن يوم واحد.</li>
     * </ul>
     */
    @Transactional
    public Session openSession(CourseGroup group, LocalDate date) {
        LocalDate sessionDate = date != null ? date : LocalDate.now();

        // الحصة المفتوحة تُذكر برسالتها الخاصة حتى لو كانت في نفس اليوم:
        // المطلوب من المستخدم حينها إغلاقها، لا الاكتفاء بعلمه أن اليوم محجوز
        Optional<Session> alreadyOpen = sessionRepository.findByGroupAndIsActiveTrue(group);
        if (alreadyOpen.isPresent()) {
            throw new IllegalStateException(I18n.format("error.session.alreadyOpen",
                    group.getName(), alreadyOpen.get().getSessionDate()));
        }

        if (sessionRepository.existsByGroupAndSessionDate(group, sessionDate)) {
            throw new IllegalStateException(I18n.format("error.session.duplicateOnDate",
                    group.getName(), sessionDate));
        }

        Session session = Session.builder()
                .group(group)
                .sessionDate(sessionDate)
                // لحظة الفتح تُقرأ من الساعة لا من التاريخ المختار: التاريخ يقول لأي يوم
                // تُحسب الحصة، وهذا يقول متى بدأت فعلاً - وهما يفترقان كلما فُتح كشف يوم مضى
                .startedAt(LocalDateTime.now())
                .isActive(true)
                .isPaidOut(false)
                .build();

        Session saved;
        try {
            saved = sessionRepository.save(session);
            sessionRepository.flush();
        } catch (DataIntegrityViolationException error) {
            if (PersistenceErrors.isConstraint(error, "uk_session_group_date")
                    || PersistenceErrors.isConstraint(error, "uk_session_active_group")) {
                throw new IllegalStateException(
                        I18n.format("error.session.concurrentOpen", group.getName()), error);
            }
            throw error;
        }
        auditService.record(AuditAction.SESSION_OPENED, saved.getId(), group.getName(),
                "date=" + saved.getSessionDate() + "; startedAt=" + saved.getStartedAt());

        return saved;
    }

    /**
     * إغلاق حصة محددة بالاسم بدلاً من "الحصة النشطة"،
     * لأن أكثر من حصة قد تكون مفتوحة في نفس اللحظة.
     */
    @Transactional
    public void closeSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.session.notFound")));

        if (!session.isActive()) {
            throw new IllegalStateException(I18n.get("error.session.alreadyClosed"));
        }

        session.setActive(false);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        auditService.record(AuditAction.SESSION_CLOSED, session.getId(),
                session.getGroup().getName(),
                "date=" + session.getSessionDate() + "; endedAt=" + session.getEndedAt());
    }

    /** كل الحصص المفتوحة حالياً */
    @Transactional(readOnly = true)
    public List<Session> getActiveSessions() {
        return sessionRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    /**
     * الحصص المطابقة للتصفية المعروضة في شاشة إدارة الحصص.
     *
     * @param date   يوم بعينه، أو {@code null} لكل الأيام
     * @param active {@code true} للمفتوحة و{@code false} للمغلقة، أو {@code null} للحالتين
     */
    @Transactional(readOnly = true)
    public List<Session> findSessions(LocalDate date, Boolean active) {
        return sessionRepository.findFiltered(date, active);
    }
}
