package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.CenterSettingsDraft;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

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
    // إعدادات السنتر وحدها تُعيد تعريف كل الأرصدة: ledgerStartDate يقرّر أيّ
    // الحركات تُحتسب أصلاً، فتبديله يغيّر رصيد كل طالب بلا أن يمسّ صفّاً واحداً
    @RequiresRole(Role.ADMIN)
    public CenterSettings save(CenterSettingsDraft draft) {
        // الصفُّ القائم يُقرأ ثم يُطبَّق عليه ما في المسودة، فما ليست صاحبةَ قراره يبقى
        CenterSettings settings = getSettings();

        settings.setCenterName(draft.centerName());
        settings.setCenterPhone(draft.centerPhone());
        settings.setLogoPath(draft.logoPath());
        settings.setBackupPath(draft.backupPath());
        settings.setAutoBackupEnabled(draft.autoBackupEnabled());
        settings.setCurrency(draft.currency());

        settings.setBackupFrequency(draft.backupFrequency());
        settings.setBackupTime(draft.backupTime());
        settings.setBackupDayOfWeek(draft.backupDayOfWeek());
        settings.setBackupDayOfMonth(draft.backupDayOfMonth());
        settings.setBackupRetentionCount(draft.backupRetentionCount());

        settings.setNotificationChannel(draft.notificationChannel());
        settings.setNotificationApiUrl(draft.notificationApiUrl());
        settings.setNotificationSenderId(draft.notificationSenderId());
        settings.setNotificationTemplateName(draft.notificationTemplateName());
        settings.setNotificationTemplateLanguage(draft.notificationTemplateLanguage());
        settings.setNotificationBodyTemplate(draft.notificationBodyTemplate());

        settings.setLedgerStartDate(draft.ledgerStartDate());

        return write(settings);
    }

    /**
     * مفتاحُ التنبيهات وموعدُ فحصها، وهما بابٌ وحدهما.
     *
     * <p>صفُّ الإعدادات واحد، وشاشتان تكتبان فيه: الإعدادات ومركزُ التنبيهات. وما دامت
     * الكتابة تمرّ بالصفّ كاملاً فإن كلَّ حفظٍ من إحداهما يمحو ما ضبطته الأخرى - وهو ما
     * كان يقع فعلاً: شاشةُ الإعدادات لا تحمل حقلَ تنبيهاتٍ واحداً، فكانت كلُّ ضغطة
     * "حفظ" فيها تُطفئ التنبيهات وتمحو {@code lastAlertScanAt}.</p>
     *
     * <p>فصارت كلُّ شاشةٍ تكتب ما تملكه وحده. والموعدُ الفارغ يعني الافتراضي، لا
     * "بلا موعد": فحصٌ مفعَّل بلا ساعة لا يقع أبداً.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public CenterSettings saveAlertScan(boolean enabled, LocalTime scanTime) {
        CenterSettings settings = getSettings();
        settings.setAlertsEnabled(enabled);
        settings.setAlertScanTime(scanTime);

        return write(settings);
    }

    /** الكتابةُ وما يتبعها: سطرُ السجل ثم الحدث الذي يعيد جدولة النسخ والفحص */
    private CenterSettings write(CenterSettings settings) {
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
