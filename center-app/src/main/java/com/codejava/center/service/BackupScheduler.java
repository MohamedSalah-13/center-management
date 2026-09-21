package com.codejava.center.service;

import com.codejava.center.core.backup.BackupSchedule;
import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** ساعة البرنامج: "هل فات موعد النسخة" سؤالٌ لا يُختبر بغير تحريك الوقت */
    private final Clock clock;

    /**
     * المؤسسات التي يعمل لها هذا المجدوِل.
     *
     * <p>موعدُ النسخة إعدادٌ يملكه كل سنتر - الساعة التي يُغلق فيها وتهدأ قاعدته - فلا
     * يوجد موعدٌ واحد لمئة سنتر. ولذلك الجدولة لكل مؤسسة لا دورةٌ واحدة تمرّ عليها:
     * دورةٌ واحدة تعني إمّا نسخاً في غير موعد أحد، وإمّا مئة قاعدة تُنسخ في اللحظة
     * نفسها. وعلى جهازٍ في سنتر المؤسسة واحدة، فهذه الخريطة مدخلٌ واحد والسلوك كما كان.</p>
     */
    private final TenantSweep tenants;

    /** لمعرفة أيّ مؤسسة حفظت إعداداتها، حين يصل حدث الحفظ على خيطها */
    private final TenantContext tenantContext;

    private final Map<TenantId, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reschedule();
    }

    /**
     * بعد الـ commit لا قبله: الجدولة على موعد لم يُحفظ فعلاً - لأن الحفظ فشل ورجع -
     * تجعل البرنامج يعمل بإعدادات لا يراها المستخدم في الشاشة.
     *
     * <p>ويُعاد جدولةُ المؤسسة التي حفظت وحدها: الحدث يُنشر داخل سياقها وهذا المستمع
     * يعمل على خيطها نفسه بعد الـ commit، فالمؤسسة معروفة. إعادةُ جدولة الجميع لأن
     * واحدة حفظت تلغي نسخاً كانت على وشك أن تُؤخذ لسناتر لم يمسّها أحد.</p>
     */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        rescheduleCurrent();
    }

    /**
     * يلغي كل الجدولة القائمة ويبنيها من إعدادات كل مؤسسة.
     * {@code synchronized} لأن الإقلاع وحفظ الإعدادات قد يلتقيان على خيطين مختلفين.
     */
    public synchronized void reschedule() {
        cancel();
        tenants.sweep(this::scheduleFor);
    }

    /** يعيد جدولة المؤسسة التي يعمل هذا الخيط لأجلها وحدها */
    public synchronized void rescheduleCurrent() {
        TenantId tenant = tenantContext.currentTenant();
        cancel(tenant);
        scheduleFor(tenant);
    }

    /** يُستدعى داخل سياق المؤسسة: {@code getSettings} تقرأ قاعدتها هي */
    private void scheduleFor(TenantId tenant) {
        CenterSettings settings = settingsService.getSettings();
        if (!settings.isAutoBackupEnabled()) {
            return;
        }
        if (settings.getBackupPath() == null || settings.getBackupPath().isBlank()) {
            log.warn("النسخ التلقائي مفعَّل بلا مجلد حفظ؛ لن تُؤخذ أي نسخة حتى يُضبط المجلد");
            return;
        }

        BackupSchedule schedule = BackupSchedules.from(settings);
        LocalDateTime lastRun = settings.getLastAutoBackupAt();

        // العمل يُنفَّذ داخل سياق المؤسسة: خيط المجدوِل يأتي من مجمّع ولا يعرف لمن يعمل
        scheduled.put(tenant, taskScheduler.schedule(
                () -> tenants.within(tenant, this::runBackup), trigger(schedule, lastRun)));
        log.info("النسخ الاحتياطي التلقائي مجدول: {}", BackupSchedules.describe(schedule));
    }

    public synchronized void cancel() {
        scheduled.keySet().forEach(this::cancel);
    }

    private void cancel(TenantId tenant) {
        ScheduledFuture<?> future = scheduled.remove(tenant);
        if (future != null) {
            future.cancel(false); // false: نسخة جارية الآن تُترك حتى تكتمل
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
            LocalDateTime now = LocalDateTime.now(clock);
            if (context.lastCompletion() == null && schedule.isOverdue(lastRun, now)) {
                return clock.instant().plus(CATCH_UP_DELAY);
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
            BackupOutcome outcome = backupService.executeBackup(settings.getBackupPath(),
                    settings.getBackupRetentionCount());
            settingsService.recordAutoBackupAt(LocalDateTime.now(clock));
            log.info("تمت النسخة الاحتياطية التلقائية: {} ({}; {})",
                    outcome.file(), outcome.pruned().details(), outcome.offsite().details());

            // النسخةُ نجحت والختمُ كُتب - ومع ذلك هي على القرص نفسه الذي تحمي منه.
            // تنبيهٌ ثانٍ لا فشلٌ في الأول: ملفٌّ موجود، وموضعُه هو المشكلة
            if (outcome.offsite().failedAfterBeingAsked()) {
                log.error("النسخة الاحتياطية لم تخرج من الجهاز: {}", outcome.offsite().problem());
                alertEngine.raise(AlertType.BACKUP_NOT_OFFSITE, AlertDraft.internal(
                        null, null, LocalDate.now(clock).toString(), outcome.offsite().problem()));
            }
        } catch (RuntimeException e) {
            // لا يُعاد الرمي: المشغّل يعتبر المهمة منتهية على أي حال، ورميه يفقد الرسالة المترجمة
            log.error("فشلت النسخة الاحتياطية التلقائية: {}", e.getMessage(), e);

            // سطرٌ في السجل لا يراه أحد. التنبيه هو ما يجعل فشلاً يتكرر كل ليلة مرئياً
            // قبل اليوم الذي تُطلب فيه النسخة. تاريخ المحاولة وحده هو الوسيط: نصّ خطأ
            // الأداة مترجَم بلغة الجهاز الذي فشل، وتخزينه يجمّد السطر على تلك اللغة
            alertEngine.raise(AlertType.BACKUP_FAILED,
                    AlertDraft.internal(null, null, LocalDate.now(clock).toString()));
        }
    }
}
