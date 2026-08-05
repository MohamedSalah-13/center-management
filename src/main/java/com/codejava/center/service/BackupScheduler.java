package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.alert.AlertDraft;
import com.codejava.center.service.alert.AlertEngine;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

/**
 * يشغّل النسخة الاحتياطية التلقائية في موعدها المضبوط في الإعدادات.
 *
 * <p>حلّ محل {@code @Scheduled(cron = "0 0 2 * * ?")} على {@code BackupService}: التعبير
 * الثابت يُقرأ مرة واحدة عند الإقلاع فلا سبيل لتغيير الموعد من الشاشة. هنا يُبنى
 * {@link Trigger} يسأل {@link BackupSchedule} عن الموعد التالي بعد كل تنفيذ، وتُعاد
 * الجدولة فور حفظ الإعدادات عبر {@link SettingsChangedEvent}.</p>
 *
 * <p><b>تعويض الموعد الفائت</b> هو أهم ما يفعله الصنف. جهاز السنتر يُطفأ آخر الليل والموعد
 * الافتراضي الثانية فجراً، فالنتيجة العملية للنسخ التلقائي قبل هذا التعديل كانت: لا نسخة
 * أبداً، والإعداد يبدو مفعَّلاً في الشاشة. الآن يُقارَن وقت آخر نسخة ناجحة بالموعد الذي كان
 * يجب أن تُؤخذ فيه، فإن فات أُخذت نسخة بعد دقائق من الإقلاع - لا فوراً، حتى لا تتزاحم مع
 * فتح البرنامج على جهاز ضعيف.</p>
 *
 * <p>الفشل يُسجَّل في السجل ولا يُحدَّث {@code lastAutoBackupAt}، فيبقى التاريخ المعروض في
 * شاشة الإعدادات قديماً وتُحاول النسخة مرة أخرى عند الإقلاع التالي. النسخة التي تفشل كل
 * ليلة بصمت أسوأ من غيابها، لأن أحداً لا يكتشف ذلك إلا يوم يحتاجها.</p>
 */
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    /** مهلة بعد الإقلاع قبل تنفيذ نسخة فات موعدها */
    private static final Duration CATCH_UP_DELAY = Duration.ofMinutes(3);

    private final TaskScheduler taskScheduler;
    private final SettingsService settingsService;
    private final BackupService backupService;
    private final AlertEngine alertEngine;

    private ScheduledFuture<?> scheduled;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reschedule();
    }

    /**
     * بعد الـ commit لا قبله: الجدولة على موعد لم يُحفظ فعلاً - لأن الحفظ فشل ورجع -
     * تجعل البرنامج يعمل بإعدادات لا يراها المستخدم في الشاشة.
     */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        reschedule();
    }

    /**
     * يلغي الجدولة القائمة ويبني واحدة من الإعدادات الحالية.
     * {@code synchronized} لأن الإقلاع وحفظ الإعدادات قد يلتقيان على خيطين مختلفين.
     */
    public synchronized void reschedule() {
        cancel();

        CenterSettings settings = settingsService.getSettings();
        if (!settings.isAutoBackupEnabled()) {
            return;
        }
        if (settings.getBackupPath() == null || settings.getBackupPath().isBlank()) {
            log.warn("النسخ التلقائي مفعَّل بلا مجلد حفظ؛ لن تُؤخذ أي نسخة حتى يُضبط المجلد");
            return;
        }

        BackupSchedule schedule = BackupSchedule.from(settings);
        LocalDateTime lastRun = settings.getLastAutoBackupAt();

        scheduled = taskScheduler.schedule(this::runBackup, trigger(schedule, lastRun));
        log.info("النسخ الاحتياطي التلقائي مجدول: {}", schedule.describe());
    }

    public synchronized void cancel() {
        if (scheduled != null) {
            scheduled.cancel(false); // false: نسخة جارية الآن تُترك حتى تكتمل
            scheduled = null;
        }
    }

    /**
     * موعد التنفيذ التالي.
     *
     * <p>{@code lastCompletion == null} يعني أن هذه أول مرة يُسأل فيها المشغّل منذ إعادة
     * الجدولة، أي أننا عند الإقلاع أو بعد حفظ الإعدادات مباشرةً - وهي اللحظة الوحيدة التي
     * يصحّ فيها التعويض عن موعد فائت.</p>
     */
    private Trigger trigger(BackupSchedule schedule, LocalDateTime lastRun) {
        return context -> {
            LocalDateTime now = LocalDateTime.now();
            if (context.lastCompletion() == null && schedule.isOverdue(lastRun, now)) {
                return Instant.now().plus(CATCH_UP_DELAY);
            }
            return schedule.nextRunAfter(now).atZone(ZoneId.systemDefault()).toInstant();
        };
    }

    private void runBackup() {
        CenterSettings settings = settingsService.getSettings();
        // قد تكون الإعدادات تغيّرت بين الجدولة والتنفيذ (ليلة كاملة بينهما)
        if (!settings.isAutoBackupEnabled() || settings.getBackupPath() == null) {
            return;
        }

        try {
            Path file = backupService.executeBackup(settings.getBackupPath(),
                    settings.getBackupRetentionCount());
            settingsService.recordAutoBackupAt(LocalDateTime.now());
            log.info("تمت النسخة الاحتياطية التلقائية: {}", file);
        } catch (RuntimeException e) {
            // لا يُعاد الرمي: المشغّل يعتبر المهمة منتهية على أي حال، ورميه يفقد الرسالة المترجمة
            log.error("فشلت النسخة الاحتياطية التلقائية: {}", e.getMessage(), e);

            // سطرٌ في السجل لا يراه أحد. التنبيه هو ما يجعل فشلاً يتكرر كل ليلة مرئياً
            // قبل اليوم الذي تُطلب فيه النسخة. تاريخ المحاولة وحده هو الوسيط: نصّ خطأ
            // الأداة مترجَم بلغة الجهاز الذي فشل، وتخزينه يجمّد السطر على تلك اللغة
            alertEngine.raise(AlertType.BACKUP_FAILED,
                    AlertDraft.internal(null, null, LocalDate.now().toString()));
        }
    }
}
