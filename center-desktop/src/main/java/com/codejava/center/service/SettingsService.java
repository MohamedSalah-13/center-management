package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * إعدادات السنتر.
 *
 * <p>كانت شاشة الإعدادات تتعامل مع الـ Repository مباشرةً، وكان كل من يحتاج اسم السنتر
 * يكرّر {@code findById(1L)} ومعالجة غيابه. الصف يوحّد ذلك ويضمن أن صف الإعدادات
 * واحد دائماً بالمعرّف 1.</p>
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final CenterSettingsRepository centerSettingsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    /** الإعدادات الحالية، أو صف افتراضي غير محفوظ إن لم تُضبط بعد */
    @Transactional(readOnly = true)
    public CenterSettings getSettings() {
        return centerSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> CenterSettings.builder().id(SETTINGS_ID).build());
    }

    /** اسم السنتر للعرض، مع بديل معقول قبل ضبط الإعدادات */
    @Transactional(readOnly = true)
    public String getCenterName() {
        String name = getSettings().getCenterName();
        // الاسم الافتراضي يتبع لغة الواجهة؛ الاسم المضبوط في الإعدادات يُعرض كما كتبه المستخدم
        return name == null || name.isBlank() ? I18n.get("settings.defaultCenterName") : name;
    }

    /**
     * المعرّف يُفرض على 1 دائماً: الإعدادات صف واحد، وتركه لما يأتي من الواجهة
     * يسمح بإنشاء صفوف إعدادات متعددة لا يقرأ النظام إلا أولها.
     */
    @Transactional
    public CenterSettings save(CenterSettings settings) {
        settings.setId(SETTINGS_ID);
        CenterSettings saved = centerSettingsRepository.save(settings);

        auditService.record(AuditAction.SETTINGS_UPDATED, saved.getId(),
                saved.getCenterName(), summarize(saved));

        eventPublisher.publishEvent(new SettingsChangedEvent(saved));
        return saved;
    }

    /**
     * ما يُكتب في سجل المراقبة عن الإعدادات بعد الحفظ.
     *
     * <p>ليست كل الحقول: {@code ledgerStartDate} وحده يغيّر رصيد كل طالب في النظام - تقديمه
     * يُسقط متأخرات قائمة دون أن يمسّ حركة واحدة في جدول الحركات - وحالة النسخ الاحتياطي
     * ومسارها هي ما يقرّر وجود نسخة أصلاً. الاسم والهاتف والشعار عرض لا أثر له.</p>
     *
     * <p>وقناة الإشعارات معها: تحويلها إلى مزوّد يجعل البرنامج يراسل أولياء الأمور بلا
     * ضغطة من موظف، وهو تغيير في من يستطيع الكلام باسم السنتر لا في شكل شاشة.</p>
     *
     * <p>والعملة كذلك: لا تمسّ رقماً واحداً في القاعدة، لكنها تغيّر ما يعنيه كل رقم فيها.
     * إيصال قديم بخمسمئة وإيصال جديد بخمسمئة يصيران غير متساويين، ولا شيء في جدول
     * الحركات يقول متى وقع ذلك ولا من فعله - إلا هذا السطر.</p>
     */
    private String summarize(CenterSettings settings) {
        return "currency=" + settings.getCurrency()
                + "; ledgerStart=" + settings.getLedgerStartDate()
                + "; autoBackup=" + settings.isAutoBackupEnabled()
                + "; backupPath=" + settings.getBackupPath()
                + "; frequency=" + settings.getBackupFrequency()
                + "; time=" + settings.getBackupTime()
                + "; notifyChannel=" + settings.getNotificationChannel()
                // التنبيهات معها: إيقاف المفتاح الرئيسي يُصمت النظام كله عن حالات قائمة،
                // وهو ما يُسأل عنه يوم يقول أحدهم "لم يصلني أي تنبيه"
                + "; alerts=" + settings.isAlertsEnabled()
                + "; alertScanTime=" + settings.getAlertScanTime();
    }

    /**
     * يسجّل لحظة آخر نسخة احتياطية تلقائية ناجحة.
     *
     * <p>لا ينشر {@link SettingsChangedEvent}: المجدوِل هو من يستدعيها بعد كل نسخة،
     * وإشعاره بها يجعله يعيد جدولة نفسه بعد كل تنفيذ بلا داعٍ.</p>
     */
    @Transactional
    public void recordAutoBackupAt(LocalDateTime moment) {
        CenterSettings settings = getSettings();
        settings.setLastAutoBackupAt(moment);
        centerSettingsRepository.save(settings);
    }

    /**
     * يسجّل لحظة آخر فحص تنبيهات مكتمل.
     *
     * <p>لا ينشر {@link SettingsChangedEvent} للسبب نفسه في {@link #recordAutoBackupAt}:
     * المجدوِل هو من يستدعيها بعد كل فحص، وإشعاره بها يجعله يعيد جدولة نفسه بعد كل
     * تنفيذ بلا داعٍ - وإعادة الجدولة تُسقط أيضاً معلومة "هذه أول مرة" التي يقوم عليها
     * تعويض الموعد الفائت.</p>
     */
    @Transactional
    public void recordAlertScanAt(LocalDateTime moment) {
        CenterSettings settings = getSettings();
        settings.setLastAlertScanAt(moment);
        centerSettingsRepository.save(settings);
    }
}
