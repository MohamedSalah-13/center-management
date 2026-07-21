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

    @Transactional
    public Session openSession(CourseGroup group, LocalDate date) {
        // التحقق من عدم وجود حصة نشطة حالياً لتجنب التعارض
        Optional<Session> activeSession = sessionRepository.findActiveSessionForToday();
        if (activeSession.isPresent()) {
            throw new IllegalStateException("يوجد حصة نشطة بالفعل. يرجى إغلاقها أولاً.");
        }

        Session session = Session.builder()
                .group(group)
                .sessionDate(date != null ? date : LocalDate.now())
                .isActive(true)
                .isPaidOut(false)
                .build();

        return sessionRepository.save(session);
    }

    @Transactional
    public void closeActiveSession() {
        Session activeSession = sessionRepository.findActiveSessionForToday()
                .orElseThrow(() -> new IllegalStateException("لا توجد حصة نشطة لإغلاقها."));

        activeSession.setActive(false);
        sessionRepository.save(activeSession);
    }

    @Transactional(readOnly = true)
    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }
}