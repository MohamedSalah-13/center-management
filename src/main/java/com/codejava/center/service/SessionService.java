package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;

    /**
     * فتح حصة جديدة لمجموعة.
     * يُسمح بعدة حصص مفتوحة في نفس الوقت (قاعات متوازية)،
     * والقيد الوحيد أن المجموعة الواحدة لا يكون لها حصتان مفتوحتان معاً.
     */
    @Transactional
    public Session openSession(CourseGroup group, LocalDate date) {
        Optional<Session> alreadyOpen = sessionRepository.findByGroupAndIsActiveTrue(group);
        if (alreadyOpen.isPresent()) {
            throw new IllegalStateException(String.format(
                    "المجموعة \"%s\" لديها حصة مفتوحة بالفعل بتاريخ %s. يرجى إغلاقها أولاً.",
                    group.getName(), alreadyOpen.get().getSessionDate()));
        }

        Session session = Session.builder()
                .group(group)
                .sessionDate(date != null ? date : LocalDate.now())
                .isActive(true)
                .isPaidOut(false)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * إغلاق حصة محددة بالاسم بدلاً من "الحصة النشطة"،
     * لأن أكثر من حصة قد تكون مفتوحة في نفس اللحظة.
     */
    @Transactional
    public void closeSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("الحصة غير موجودة."));

        if (!session.isActive()) {
            throw new IllegalStateException("هذه الحصة مغلقة بالفعل.");
        }

        session.setActive(false);
        sessionRepository.save(session);
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
}
