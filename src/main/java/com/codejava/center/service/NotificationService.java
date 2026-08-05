package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.NotificationLog;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.AlertType;
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
import com.codejava.center.util.I18n;
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
    private final SettingsService settingsService;
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
                AlertType.ABSENCE, LocalDateTime.now().minusDays(ABSENCE_COOLDOWN_DAYS));

        String centerName = centerName();
        List<NotificationCandidate> candidates = new ArrayList<>();

        for (AttendanceSummary row : report.rows()) {
            long absences = Math.max(0, report.totalSessions() - row.attended());
            if (absences == 0) {
                continue;
            }

            // اسم السنتر أولاً ورمز العملة أخيراً - نفس ترتيب AlertEngine.messageParent،
            // فقالب واحد يخدم المسارين. الغياب لا يذكر مالاً فيتجاهل الوسيط الأخير.
            String message = I18n.format("alert.parentMessage.ABSENCE",
                    centerName, row.studentName(), absences, report.totalSessions(),
                    report.groupName(), MoneyUtils.currencySymbol());

            candidates.add(toCandidate(row.studentId(), row.studentName(), AlertType.ABSENCE,
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
                AlertType.ARREARS, LocalDateTime.now().minusDays(ARREARS_COOLDOWN_DAYS));

        String centerName = centerName();
        List<NotificationCandidate> candidates = new ArrayList<>();

        for (StudentBalance row : arrears) {
            String message = I18n.format("alert.parentMessage.ARREARS",
                    centerName, row.studentName(), MoneyUtils.format(row.amountDue()),
                    MoneyUtils.currencySymbol());

            candidates.add(toCandidate(row.studentId(), row.studentName(), AlertType.ARREARS,
                    row.parentPhone(), message, alreadyNotified));
        }

        return candidates;
    }

    private NotificationCandidate toCandidate(Long studentId, String studentName, AlertType type,
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
        return deliver(candidate);
    }

    /**
     * الإرسال نفسه، بلا حارس صلاحية، لمسار التنبيهات التلقائي.
     *
     * <p>غير محروسة عن قصد وللسبب نفسه الذي جعل {@code BackupService.executeBackup}
     * كذلك: {@code AlertScheduler} ينادي عليها من خيط المجدوِل حيث لا جلسة مستخدم
     * أصلاً، فأي {@code @RequiresRole} هنا يعني أن كل تنبيه مجدول يُرفض.</p>
     *
     * <p>وليست ثغرة: لا يبلغها إلا محرّك التنبيهات، ولا يُرسل إلا ما فعّله المدير
     * صراحةً في شاشة إدارة القواعد - وهي محروسة. الحارس على قرار الإرسال لا على
     * فعل الإرسال.</p>
     */
    @Transactional
    public MessageSender.SendResult sendAutomatic(NotificationCandidate candidate) {
        return deliver(candidate);
    }

    private MessageSender.SendResult deliver(NotificationCandidate candidate) {
        if (!candidate.phoneValid()) {
            return MessageSender.SendResult.failed(
                    I18n.format("error.notification.invalidPhone", candidate.rawPhone()));
        }

        MessageSender.SendResult result = messageSender.send(
                candidate.internationalPhone(), candidate.message());

        if (result.success()) {
            Student student = studentRepository.findById(candidate.studentId())
                    .orElseThrow(() -> new IllegalStateException(I18n.get("error.notification.studentNotFound")));

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

    /**
     * إرسال رسالة تجريبية إلى رقم يكتبه المدير، بالقناة والضبط المحفوظين.
     *
     * <p>لا تُسجَّل في {@code notification_logs}: السجل يخصّ إشعارات الطلاب ويمنع
     * تكرارها، ورسالة اختبار لا طالب لها ولا يصح أن تحجب إشعاراً حقيقياً.</p>
     *
     * <p>هي الطريقة الوحيدة لمعرفة أن ضبط المزوّد صحيح قبل يوم الإرسال: مفتاح منتهٍ أو
     * قالب غير معتمَد لا يظهر أيٌّ منهما إلا في ردّ المزوّد على رسالة حقيقية.</p>
     */
    @RequiresRole(Role.ADMIN)
    public MessageSender.SendResult sendTestMessage(String rawPhone) {
        Optional<String> international = PhoneNumbers.toInternational(rawPhone);
        if (international.isEmpty()) {
            return MessageSender.SendResult.failed(
                    I18n.format("error.notification.invalidPhone", rawPhone == null ? "" : rawPhone));
        }
        return messageSender.send(international.get(),
                I18n.format("notify.message.test", centerName()));
    }

    /** هل تتطلب القناة الحالية ضغط المستخدم على "إرسال" لكل رسالة؟ */
    public boolean channelRequiresManualConfirmation() {
        return messageSender.requiresManualConfirmation();
    }

    /** ما ينقص القناة الحالية لتعمل، أو {@code Optional} فارغ إن كانت جاهزة */
    public Optional<String> channelProblem() {
        return messageSender.configurationProblem();
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<NotificationLog> getRecentNotifications() {
        return notificationLogRepository.findRecent(LocalDate.now().minusDays(30).atStartOfDay());
    }

    private String centerName() {
        return settingsService.getCenterName();
    }
}
