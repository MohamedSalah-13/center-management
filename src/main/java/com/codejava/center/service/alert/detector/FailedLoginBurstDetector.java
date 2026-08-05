package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.AuditLogRepository;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * محاولات دخول فاشلة متكررة خلال الفترة.
 *
 * <p>يُعدّ من {@code audit_logs} لا من جدول جديد: السجل يحمل الواقعة أصلاً
 * ({@code LOGIN_FAILED})، وعدّادٌ ثانٍ لنفس الشيء يفتح باب اختلافهما - فيقول أحدهما
 * إن شيئاً وقع ويقول الآخر إنه لم يقع، وهي أسوأ حالة يمكن أن يصل إليها تنبيه أمني.</p>
 *
 * <p>ما يكشفه عملياً ليس مهاجماً بعيداً - البرنامج على شبكة السنتر - بل من يجرّب كلمات
 * مرور على جهاز في الاستقبال بعد انصراف الموظفين. ولذلك السطر في الصندوق يكفي: صاحب
 * السنتر يفتح {@code audit_logs} فيرى الأسماء والأوقات.</p>
 */
@Component
@RequiredArgsConstructor
public class FailedLoginBurstDetector implements AlertDetector {

    private final AuditLogRepository auditLogRepository;

    @Override
    public AlertType type() {
        return AlertType.FAILED_LOGIN_BURST;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        int days = Math.max(1, rule.windowDaysOrDefault());
        int limit = Math.max(1, rule.thresholdOrDefault());

        long attempts = auditLogRepository.countByActionSince(
                AuditAction.LOGIN_FAILED, LocalDateTime.now().minusDays(days));

        return attempts < limit
                ? List.of()
                : List.of(AlertDraft.internal(null, null, String.valueOf(attempts), String.valueOf(days)));
    }
}
