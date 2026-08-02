package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.NotificationLog;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.NotificationType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.NotificationLogRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.service.notification.PhoneNumbers;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * إشعارات أولياء الأمور.
 *
 * <p>يحدّد من يستحق الإشعار وبأي نص، ويسجّل ما أُرسل. الإرسال الفعلي يفوَّض إلى
 * {@link MessageSender} فيبقى هذا الصنف مستقلاً عن المزوّد.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final StudentRepository studentRepository;
    private final CenterSettingsRepository centerSettingsRepository;
    private final MessageSender messageSender;

    /** لا يُرسل إشعار غياب أكثر من مرة في اليوم لنفس الطالب */
    private static final int ABSENCE_COOLDOWN_DAYS = 1;
    /** تذكير المتأخرات أقل إلحاحاً: مرة كل أسبوع */
    private static final int ARREARS_COOLDOWN_DAYS = 7;

    /**
     * مرشّحو إشعار الغياب: من تغيّب حصةً واحدة على الأقل خلال فترة التقرير.
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<NotificationCandidate> buildAbsenceNotifications(GroupAttendanceReport report) {
        Set<Long> alreadyNotified = notificationLogRepository.findNotifiedStudentIds(
                NotificationType.ABSENCE, LocalDateTime.now().minusDays(ABSENCE_COOLDOWN_DAYS));

        String centerName = centerName();
        List<NotificationCandidate> candidates = new ArrayList<>();

        for (AttendanceSummary row : report.rows()) {
            long absences = Math.max(0, report.totalSessions() - row.attended());
            if (absences == 0) {
                continue;
            }

            String message = String.format(
                    "السلام عليكم، %s يفيدكم بأن الطالب/ة %s تغيّب عن %d حصة من أصل %d في مجموعة %s. برجاء المتابعة. شكراً لتعاونكم.",
                    centerName, row.studentName(), absences, report.totalSessions(), report.groupName());

            candidates.add(toCandidate(row.studentId(), row.studentName(), NotificationType.ABSENCE,
                    row.parentPhone(), message, alreadyNotified));
        }

        return candidates;
    }

    /**
     * مرشّحو تذكير المتأخرات: من عليه مبلغ مستحق.
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<NotificationCandidate> buildArrearsNotifications(List<StudentBalance> arrears) {
        Set<Long> alreadyNotified = notificationLogRepository.findNotifiedStudentIds(
                NotificationType.ARREARS, LocalDateTime.now().minusDays(ARREARS_COOLDOWN_DAYS));

        String centerName = centerName();
        List<NotificationCandidate> candidates = new ArrayList<>();

        for (StudentBalance row : arrears) {
            String message = String.format(
                    "السلام عليكم، %s يفيدكم بأن الطالب/ة %s عليه مستحقات بقيمة %s. برجاء السداد لاستمرار حضور الحصص. شكراً لتعاونكم.",
                    centerName, row.studentName(), MoneyUtils.formatWithCurrency(row.amountDue()));

            candidates.add(toCandidate(row.studentId(), row.studentName(), NotificationType.ARREARS,
                    row.parentPhone(), message, alreadyNotified));
        }

        return candidates;
    }

    private NotificationCandidate toCandidate(Long studentId, String studentName, NotificationType type,
                                              String rawPhone, String message, Set<Long> alreadyNotified) {
        Optional<String> international = PhoneNumbers.toInternational(rawPhone);

        return new NotificationCandidate(
                studentId, studentName, type,
                rawPhone == null ? "" : rawPhone,
                international.orElse(""),
                message,
                international.isPresent(),
                alreadyNotified.contains(studentId));
    }

    /**
     * إرسال إشعار واحد وتسجيله.
     * لا يُسجَّل إلا ما نجح إرساله فعلاً، وإلا امتنع النظام عن إعادة المحاولة
     * ظاناً أن ولي الأمر أُبلغ.
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public MessageSender.SendResult send(NotificationCandidate candidate) {
        if (!candidate.phoneValid()) {
            return MessageSender.SendResult.failed("رقم ولي الأمر غير صالح: " + candidate.rawPhone());
        }

        MessageSender.SendResult result = messageSender.send(
                candidate.internationalPhone(), candidate.message());

        if (result.success()) {
            Student student = studentRepository.findById(candidate.studentId())
                    .orElseThrow(() -> new IllegalStateException("الطالب غير موجود"));

            notificationLogRepository.save(NotificationLog.builder()
                    .student(student)
                    .type(candidate.type())
                    .recipientPhone(candidate.internationalPhone())
                    .message(candidate.message())
                    .sentAt(LocalDateTime.now())
                    .channel(messageSender.channelName())
                    .build());
        }

        return result;
    }

    /** هل تتطلب القناة الحالية ضغط المستخدم على "إرسال" لكل رسالة؟ */
    public boolean channelRequiresManualConfirmation() {
        return messageSender.requiresManualConfirmation();
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<NotificationLog> getRecentNotifications() {
        return notificationLogRepository.findRecent(LocalDate.now().minusDays(30).atStartOfDay());
    }

    private String centerName() {
        return centerSettingsRepository.findById(1L)
                .map(CenterSettings::getCenterName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("السنتر التعليمي");
    }
}
