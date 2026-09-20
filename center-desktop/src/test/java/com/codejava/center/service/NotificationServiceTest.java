package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.NotificationLogRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * منطق تحديد من يُبلَّغ. الخطأ هنا يصل شخصاً حقيقياً: رسالة مكررة، أو رقم خاطئ،
 * أو تسجيل إرسال لم يحدث فيمتنع النظام عن إعادة المحاولة.
 */
@DataJpaTest
@Import({NotificationService.class, SettingsService.class, AuditService.class, SecurityConfig.class,
        UserSession.class, NotificationServiceTest.RecordingSender.class})
@EnableAspectJAutoProxy
class NotificationServiceTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserSession userSession;
    @Autowired private RecordingSender sender;

    @BeforeEach
    void loginAsAdmin() {
        // الخدمة محمية بـ @RequiresRole(ADMIN)
        userSession.setCurrentUser(User.builder()
                .id(1L).username("admin").password("x").role(Role.ADMIN).build());
        sender.reset();
    }

    @AfterEach
    void logout() {
        userSession.cleanUserSession();
    }

    @Test
    void buildsAbsenceMessageOnlyForStudentsWhoMissedSessions() {
        Student absentee = persistStudent("STU-N1", "غائب", "01012345678");
        Student perfect = persistStudent("STU-N2", "منتظم", "01112345678");

        var report = new GroupAttendanceReport("مجموعة أ", 4, List.of(
                new AttendanceSummary(absentee.getId(), "غائب", "STU-N1", "01012345678", 1),
                new AttendanceSummary(perfect.getId(), "منتظم", "STU-N2", "01112345678", 4)));

        var candidates = notificationService.buildAbsenceNotifications(report);

        assertThat(candidates).extracting(NotificationCandidate::studentName).containsExactly("غائب");
        // ثلاث غيابات من أربع حصص - النص يُبنى من نفس مفتاح الحزمة فلا يتعلّق بلغة الجهاز
        assertThat(candidates.get(0).message()).isEqualTo(I18n.format("alert.parentMessage.ABSENCE",
                I18n.get("settings.defaultCenterName"), "غائب", 3L, 4L, "مجموعة أ"));
        assertThat(candidates.get(0).sendable()).isTrue();
    }

    @Test
    void marksInvalidParentPhoneAsNotSendable() {
        Student student = persistStudent("STU-N3", "طالب", "0221234567"); // أرضي لا محمول

        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N3", "0221234567", 0)));

        var candidate = notificationService.buildAbsenceNotifications(report).get(0);

        assertThat(candidate.phoneValid()).isFalse();
        assertThat(candidate.sendable()).isFalse();
        assertThat(candidate.statusLabel()).isEqualTo(I18n.get("notify.status.invalidPhone"));
    }

    /** بدون منع التكرار يصل ولي الأمر رسالة في كل مرة يُفتح فيها التقرير */
    @Test
    void doesNotResendTheSameNotificationWithinTheCooldown() {
        Student student = persistStudent("STU-N4", "طالب", "01012345678");
        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N4", "01012345678", 0)));

        NotificationCandidate first = notificationService.buildAbsenceNotifications(report).get(0);
        assertThat(notificationService.send(first).success()).isTrue();

        NotificationCandidate second = notificationService.buildAbsenceNotifications(report).get(0);
        assertThat(second.alreadyNotified()).isTrue();
        assertThat(second.sendable()).isFalse();
    }

    /** تسجيل إرسال لم يحدث يجعل النظام يمتنع عن إعادة المحاولة ظاناً أن الأهل أُبلغوا */
    @Test
    void failedSendIsNotLogged() {
        Student student = persistStudent("STU-N5", "طالب", "01012345678");
        sender.failWith("تعذر الاتصال بمزوّد الرسائل");

        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N5", "01012345678", 0)));

        var result = notificationService.send(notificationService.buildAbsenceNotifications(report).get(0));

        assertThat(result.success()).isFalse();
        assertThat(notificationLogRepository.count()).isZero();
    }

    /**
     * قناة الرابط: السطر يُكتب حين تُفتح المحادثة، لا حين يُبنى الرابط.
     *
     * <p>هذا هو الفرق كله بعد إخراج الفتح من الخدمة. الرابط يعود إلى الشاشة، فإن فشل
     * الفتح — تطبيق غير مثبَّت، أو رفض معالج البروتوكول — فلا سطر، والمحاولة تُعاد غداً.
     * وكتابة السطر عند التجهيز كانت ستمنع الإعادة وتترك وليَّ أمرٍ بلا خبر ولا أحد
     * يعرف.</p>
     */
    @Test
    void handOffIsLoggedOnlyAfterTheScreenSaysTheChatWasOpened() {
        Student student = persistStudent("STU-N8", "طالب", "01012345678");
        sender.handOffTo("https://wa.me/201012345678?text=x");

        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N8", "01012345678", 0)));
        NotificationCandidate candidate = notificationService.buildAbsenceNotifications(report).get(0);

        var result = notificationService.send(candidate);

        assertThat(result.needsHandOff()).isTrue();
        assertThat(result.link()).isEqualTo("https://wa.me/201012345678?text=x");
        assertThat(result.success()).isFalse();
        assertThat(notificationLogRepository.count()).as("قبل الفتح").isZero();

        notificationService.recordOpened(candidate);

        assertThat(notificationLogRepository.count()).as("بعد الفتح").isEqualTo(1);
    }

    /**
     * الفحص التلقائي لا يُسلّم إلى قناة تحتاج إنساناً.
     *
     * <p>{@code AlertScheduler} يعمل من خيط المجدوِل، وقد يكون ذلك ليلاً أو على طرفية
     * الاستقبال. قناة الرابط هناك تجهّز رابطاً لا يفتحه أحد، فالقول إنها لا تصلح أصدق
     * من نتيجةٍ تبدو إرسالاً ولم تكن - والمزوّد وحده هو ما يُرسل صامتاً.</p>
     */
    @Test
    void automaticSendRefusesAChannelThatNeedsSomebodyAtTheScreen() {
        Student student = persistStudent("STU-N9", "طالب", "01012345678");
        sender.manual = true;

        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N9", "01012345678", 0)));

        var result = notificationService.sendAutomatic(
                notificationService.buildAbsenceNotifications(report).get(0));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(I18n.get("error.notification.channelNeedsPerson"));
        assertThat(sender.sentTo).as("لم يُبنَ رابط أصلاً").isEmpty();
        assertThat(notificationLogRepository.count()).isZero();
    }

    @Test
    void sendsPhoneInInternationalFormatNotAsStored() {
        Student student = persistStudent("STU-N6", "طالب", "01012345678");
        var report = new GroupAttendanceReport("مجموعة أ", 2, List.of(
                new AttendanceSummary(student.getId(), "طالب", "STU-N6", "01012345678", 0)));

        notificationService.send(notificationService.buildAbsenceNotifications(report).get(0));

        assertThat(sender.sentTo).containsExactly("201012345678");
        assertThat(notificationLogRepository.findAll().get(0).getRecipientPhone()).isEqualTo("201012345678");
    }

    @Test
    void arrearsMessageStatesTheAmountDue() {
        Student student = persistStudent("STU-N7", "مدين", "01012345678");

        var candidates = notificationService.buildArrearsNotifications(List.of(
                new StudentBalance(student.getId(), "مدين", "STU-N7", "0100", "01012345678",
                        new BigDecimal("-75.00"))));

        assertThat(candidates.get(0).message())
                .contains(MoneyUtils.formatWithCurrency(new BigDecimal("75.00")));
        assertThat(candidates.get(0).sendable()).isTrue();
    }

    private Student persistStudent(String barcode, String name, String parentPhone) {
        return studentRepository.saveAndFlush(Student.builder()
                .barcode(barcode).name(name).parentPhone(parentPhone).isActive(true).build());
    }

    /** بديل اختباري عن المزوّد الحقيقي: لا يفتح متصفحاً ولا يرسل شيئاً */
    static class RecordingSender implements MessageSender {
        final List<String> sentTo = new ArrayList<>();
        private String failureReason;
        private String handOffLink;
        boolean manual;

        void reset() {
            sentTo.clear();
            failureReason = null;
            handOffLink = null;
            manual = false;
        }

        void failWith(String reason) {
            this.failureReason = reason;
        }

        /** يحاكي قناة الرابط: لا يُرسل شيئاً ويعيد ما على الشاشة فتحه */
        void handOffTo(String link) {
            this.handOffLink = link;
            this.manual = true;
        }

        @Override
        public SendResult send(String internationalPhone, String message) {
            if (failureReason != null) {
                return SendResult.failed(failureReason);
            }
            sentTo.add(internationalPhone);
            return handOffLink == null ? SendResult.ok() : SendResult.handOff(handOffLink);
        }

        @Override
        public String channelName() {
            return "TEST";
        }

        @Override
        public boolean requiresManualConfirmation() {
            return manual;
        }

        @Override
        public java.util.Optional<String> configurationProblem() {
            return java.util.Optional.empty();
        }
    }
}
