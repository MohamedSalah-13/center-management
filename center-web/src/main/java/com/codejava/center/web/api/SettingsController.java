package com.codejava.center.web.api;

import com.codejava.center.core.backup.BackupFrequency;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.Currency;
import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.dto.CenterSettingsDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * إعدادات السنتر: صفٌّ واحد في القاعدة، وشاشتان تكتبان فيه.
 *
 * <p>ولذلك يأخذ {@code PUT} مسودةً لا كياناً، و<b>المسودة لا تحمل مفتاحَ التنبيهات ولا
 * موعدَ فحصها ولا ختمَي المجدوِلين</b>. الكتابةُ بالصفّ كاملاً كانت تعني أن كل حفظٍ من
 * شاشةٍ يمحو ما ضبطته الأخرى - انظر {@code CenterSettingsDraft} و
 * {@code SettingsService.saveAlertScan}.</p>
 *
 * <p>والقراءة تُسلّم ختمَي المجدوِلين <b>عرضاً لا تعديلاً</b>: تاريخٌ قديم في
 * {@code lastAutoBackupAt} هو الشيء الوحيد الذي يقول إن النسخ الليلية تفشل ليلةً بعد
 * ليلة، فإخفاؤه يجعل الفشل صامتاً تماماً.</p>
 *
 * <p>وما ليس هنا ولن يكون: كلمةُ مرور التشفير ومفتاحُ المزوّد. كلاهما سرٌّ يعيش على
 * الجهاز ({@code BackupSecretStore} و{@code MessagingSecretStore})، والخادم يجيبهما من
 * البيئة - وسرٌّ يمرّ في جسم طلبٍ يصير سطراً في سجلّ كل وسيط بينهما.</p>
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /** جسمُ الطلب: ما تملكه شاشةُ الإعدادات، ولا معرّف فيه - الصفُّ واحد */
    public record SettingsRequest(
            @Size(max = 255) String centerName,
            @Size(max = 255) String centerPhone,
            @Size(max = 255) String logoPath,
            @Size(max = 255) String backupPath,
            boolean autoBackupEnabled,
            Currency currency,
            BackupFrequency backupFrequency,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime backupTime,
            Integer backupDayOfWeek,
            Integer backupDayOfMonth,
            Integer backupRetentionCount,
            NotificationChannel notificationChannel,
            @Size(max = 500) String notificationApiUrl,
            @Size(max = 100) String notificationSenderId,
            @Size(max = 100) String notificationTemplateName,
            @Size(max = 20) String notificationTemplateLanguage,
            @Size(max = 500) String notificationBodyTemplate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ledgerStartDate) {

        CenterSettingsDraft toDraft() {
            return new CenterSettingsDraft(centerName, centerPhone, logoPath, backupPath,
                    autoBackupEnabled, currency, backupFrequency, backupTime, backupDayOfWeek,
                    backupDayOfMonth, backupRetentionCount, notificationChannel, notificationApiUrl,
                    notificationSenderId, notificationTemplateName, notificationTemplateLanguage,
                    notificationBodyTemplate, ledgerStartDate);
        }
    }

    /**
     * @param lastAutoBackupAt آخر نسخة تلقائية ناجحة - عرضٌ لا يُرسَل في الحفظ
     * @param lastAlertScanAt  آخر فحص تنبيهات مكتمل - كذلك
     */
    public record SettingsView(String centerName, String centerPhone, String logoPath,
                               String backupPath, boolean autoBackupEnabled,
                               String currencyName, String currency,
                               String backupFrequency, LocalTime backupTime,
                               Integer backupDayOfWeek, Integer backupDayOfMonth,
                               Integer backupRetentionCount, LocalDateTime lastAutoBackupAt,
                               String notificationChannel, String notificationApiUrl,
                               String notificationSenderId, String notificationTemplateName,
                               String notificationTemplateLanguage, String notificationBodyTemplate,
                               boolean alertsEnabled, LocalTime alertScanTime,
                               LocalDateTime lastAlertScanAt,
                               LocalDate ledgerStartDate) {
    }

    /** الثابتُ يُرسَل والمترجَمُ يُعرض - كالصفوف الدراسية وأيام الأسبوع */
    public record OptionView(String name, String label) {
    }

    /**
     * القراءة مفتوحة كبقية القراءات: اسمُ السنتر وهاتفه يُطبعان على كل ورقة، وموعدُ
     * النسخ الاحتياطي ليس سرّاً - والسرّان الحقيقيان ليسا في هذا الصفّ أصلاً.
     */
    @GetMapping
    public SettingsView settings() {
        return view(settingsService.getSettings());
    }

    @PutMapping
    public SettingsView save(@Valid @RequestBody SettingsRequest request) {
        return view(settingsService.save(request.toDraft()));
    }

    @GetMapping("/currencies")
    public List<OptionView> currencies() {
        return Arrays.stream(Currency.values())
                .map(currency -> new OptionView(currency.name(), currency.getDisplayName()))
                .toList();
    }

    @GetMapping("/channels")
    public List<OptionView> channels() {
        return Arrays.stream(NotificationChannel.values())
                .map(channel -> new OptionView(channel.name(), channel.getDisplayName()))
                .toList();
    }

    /**
     * العملةُ الغائبة تعني الافتراضية لا "بلا عملة": قاعدةٌ مُرقّاة لا تحمل قيمة وكل
     * مبالغها بالجنيه فعلاً، فالقائمة تُظهر ما يُطبع اليوم على الإيصالات.
     */
    private static SettingsView view(CenterSettings settings) {
        Currency currency = settings.getCurrency() == null
                ? Currency.DEFAULT
                : settings.getCurrency();

        return new SettingsView(settings.getCenterName(), settings.getCenterPhone(),
                settings.getLogoPath(), settings.getBackupPath(), settings.isAutoBackupEnabled(),
                currency.name(), currency.getDisplayName(),
                settings.getBackupFrequency() == null ? null : settings.getBackupFrequency().name(),
                settings.getBackupTime(), settings.getBackupDayOfWeek(),
                settings.getBackupDayOfMonth(), settings.getBackupRetentionCount(),
                settings.getLastAutoBackupAt(),
                settings.getNotificationChannel() == null ? null
                        : settings.getNotificationChannel().name(),
                settings.getNotificationApiUrl(), settings.getNotificationSenderId(),
                settings.getNotificationTemplateName(), settings.getNotificationTemplateLanguage(),
                settings.getNotificationBodyTemplate(),
                settings.isAlertsEnabled(), settings.getAlertScanTime(),
                settings.getLastAlertScanAt(),
                settings.getLedgerStartDate());
    }
}
