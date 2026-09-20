package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * مضى على آخر نسخة احتياطية ناجحة أكثر مما ينبغي.
 *
 * <p>هذا هو التنبيه الذي يجعل النسخ الاحتياطي مضموناً بدل أن يكون مفترضاً.
 * {@code BACKUP_FAILED} يُطلق لحظة فشل محاولة، وهو لا يكفي: أسوأ الأعطال هو ألا تقع
 * محاولة أصلاً - جهاز يُطفأ كل ليلة، أو مهمة أُلغيت ولم يُعَد جدولتها. عندئذ لا يوجد
 * فشل يُبلَّغ عنه، ويبدو كل شيء سليماً حتى اليوم الذي تُطلب فيه النسخة.</p>
 *
 * <p>لا يُطلق شيئاً حين يكون النسخ التلقائي مُوقفاً: سنتر اختار أن ينسخ يدوياً على
 * فلاشة اتّخذ قراراً، وتنبيهه به يومياً يعلّم المستخدم تجاهل التنبيهات.</p>
 *
 * <p>وسيط الرسالة تاريخ آخر نسخة بصيغة ISO، أو شرطة إن لم تُؤخذ أي نسخة قطّ. تاريخٌ
 * لا عدد أيام: العدد يتغيّر كل يوم فيصير كل تنبيه سطراً جديداً لا يقول شيئاً جديداً،
 * والتاريخ يبقى هو نفسه ما دامت المشكلة هي نفسها.</p>
 */
@Component
@RequiredArgsConstructor
public class BackupOverdueDetector implements AlertDetector {

    /** ما يُكتب حين لم تُؤخذ أي نسخة قطّ؛ محايد لغوياً فيصلح للتخزين */
    private static final String NEVER = "-";

    private final SettingsService settingsService;

    @Override
    public AlertType type() {
        return AlertType.BACKUP_OVERDUE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        CenterSettings settings = settingsService.getSettings();
        if (!settings.isAutoBackupEnabled()) {
            return List.of();
        }

        LocalDateTime lastRun = settings.getLastAutoBackupAt();
        LocalDateTime deadline = LocalDateTime.now().minusDays(Math.max(1, rule.windowDaysOrDefault()));

        if (lastRun != null && lastRun.isAfter(deadline)) {
            return List.of();
        }

        String when = lastRun == null ? NEVER : lastRun.toLocalDate().toString();
        return List.of(AlertDraft.internal(null, null, when));
    }
}
