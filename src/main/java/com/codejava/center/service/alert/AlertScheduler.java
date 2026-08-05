package com.codejava.center.service.alert;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.service.SettingsChangedEvent;
import com.codejava.center.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

/**
 * يشغّل فحص التنبيهات في موعده المضبوط في الإعدادات.
 *
 * <p>مبنيّ على {@code BackupScheduler} حرفياً، ولنفس الأسباب: {@code @Scheduled} بتعبير
 * cron ثابت يُقرأ مرة عند الإقلاع فلا سبيل لتغيير الموعد من الشاشة، والجهاز في السنتر
 * يُطفأ فتضيع المواعيد التي تمرّ وهو مغلق.</p>
 *
 * <p><b>تعويض الموعد الفائت هو أهمّ ما يفعله.</b> بدونه يكون السلوك العملي: لا فحص
 * أبداً على جهاز يُفتح بعد الثامنة صباحاً، وكل القواعد تبدو مفعَّلة في الشاشة. ولذلك
 * أيضاً {@code lastAlertScanAt} لا يُكتب إلا بعد فحص اكتمل: الفشل يجب أن يُبقي الموعد
 * فائتاً فتُعاد المحاولة، لا أن يُسجَّل كأنه تمّ.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    /**
     * مهلة بعد الإقلاع قبل تنفيذ فحص فات موعده. أطول من مهلة النسخ الاحتياطي (٣ دقائق)
     * عن قصد: الاثنان قد يكونان فائتَين معاً على جهاز أُقلع صباحاً، وتزاحمهما على
     * جهاز واحد يعني نسخة احتياطية تبطئ فحصاً وفحصاً يبطئ فتح البرنامج.
     */
    private static final Duration CATCH_UP_DELAY = Duration.ofMinutes(6);

    /**
     * النبضة القصيرة للأنواع التي دقّتها بالدقائق.
     *
     * <p>خمس دقائق تحدّ دقّة كل تنبيه من هذه الأنواع: "نبّهني قبل الحصة بربع ساعة" تصل
     * بين ١٥ و١٠ دقائق قبلها. أقصر من ذلك استعلامان لكل مجموعة كل دقيقتين على جهاز
     * تشتغل عليه بوابة حضور، وأطول يجعل تذكيراً بربع ساعة يصل بعد بدء الحصة.</p>
     */
    private static final Duration FREQUENT_INTERVAL = Duration.ofMinutes(5);

    private final TaskScheduler taskScheduler;
    private final SettingsService settingsService;
    private final AlertEngine alertEngine;

    private ScheduledFuture<?> scheduled;
    private ScheduledFuture<?> frequent;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reschedule();
    }

    /** بعد الـ commit لا قبله: الجدولة على موعد لم يُحفظ فعلاً تُخالف ما يراه المستخدم */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        reschedule();
    }

    /** {@code synchronized} لأن الإقلاع وحفظ الإعدادات قد يلتقيان على خيطين مختلفين */
    public synchronized void reschedule() {
        cancel();

        CenterSettings settings = settingsService.getSettings();
        if (!settings.isAlertsEnabled()) {
            return;
        }

        AlertSchedule schedule = AlertSchedule.from(settings);
        scheduled = taskScheduler.schedule(this::runScan,
                trigger(schedule, settings.getLastAlertScanAt()));

        // النبضة القصيرة بلا موعد ولا تعويض: ما تفحصه لا يُعوَّض أصلاً. تنبيهٌ بأن حصةً
        // تبدأ بعد ربع ساعة، يصل بعد ساعتين من تشغيل البرنامج، خبرٌ لا تنبيه - وأسوأ من
        // الصمت لأنه يُقرأ على أنه الآن
        frequent = taskScheduler.scheduleWithFixedDelay(this::runFrequentScan,
                Instant.now().plus(FREQUENT_INTERVAL), FREQUENT_INTERVAL);

        log.info("فحص التنبيهات التلقائي مجدول: {}", schedule.describe());
    }

    public synchronized void cancel() {
        // false في الاثنين: فحص جارٍ الآن يُترك حتى يكتمل
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        if (frequent != null) {
            frequent.cancel(false);
            frequent = null;
        }
    }

    /**
     * {@code lastCompletion == null} يعني أن هذه أول مرة يُسأل فيها المشغّل منذ إعادة
     * الجدولة - أي عند الإقلاع أو بعد حفظ الإعدادات مباشرةً - وهي اللحظة الوحيدة التي
     * يصحّ فيها التعويض عن موعد فائت.
     */
    private Trigger trigger(AlertSchedule schedule, LocalDateTime lastRun) {
        return context -> {
            LocalDateTime now = LocalDateTime.now();
            if (context.lastCompletion() == null && schedule.isOverdue(lastRun, now)) {
                return Instant.now().plus(CATCH_UP_DELAY);
            }
            return schedule.nextRunAfter(now).atZone(ZoneId.systemDefault()).toInstant();
        };
    }

    /**
     * النبضة القصيرة.
     *
     * <p>لا تكتب {@code lastAlertScanAt}: ذاك علامةُ الفحص اليومي وعليها يقوم تعويض
     * الموعد الفائت، وكتابتها هنا كل خمس دقائق تجعل الفحص اليومي يبدو دائماً وقد تمّ
     * توّاً - فلا يُعوَّض أبداً ولا يقع أبداً على جهاز يُفتح بعد موعده.</p>
     */
    private void runFrequentScan() {
        if (!settingsService.getSettings().isAlertsEnabled()) {
            return;
        }

        try {
            AlertScanResult result = alertEngine.scanFrequent();
            if (result.raised() > 0 || result.messaged() > 0) {
                log.info("النبضة القصيرة: {} تنبيهاً، {} رسالة", result.raised(), result.messaged());
            }
        } catch (RuntimeException e) {
            // كل خمس دقائق: يُسجَّل ولا يُرمى، وإلا امتلأ السجل بنفس السطر ألف مرة في اليوم
            log.warn("فشلت النبضة القصيرة للتنبيهات: {}", e.getMessage());
        }
    }

    private void runScan() {
        // قد تكون الإعدادات تغيّرت بين الجدولة والتنفيذ (ليلة كاملة بينهما)
        if (!settingsService.getSettings().isAlertsEnabled()) {
            return;
        }

        try {
            AlertScanResult result = alertEngine.scanAll();
            settingsService.recordAlertScanAt(LocalDateTime.now());

            log.info("اكتمل فحص التنبيهات: {} تنبيهاً، {} رسالة، {} فشل",
                    result.raised(), result.messaged(), result.failures().size());
            result.failures().forEach(failure -> log.warn("فشل في فحص التنبيهات: {}", failure));
        } catch (RuntimeException e) {
            // لا يُعاد الرمي: المشغّل يعتبر المهمة منتهية على أي حال.
            // ولا يُكتب lastAlertScanAt، فيبقى الموعد فائتاً وتُعاد المحاولة عند الإقلاع التالي
            log.error("فشل فحص التنبيهات التلقائي: {}", e.getMessage(), e);
        }
    }
}
