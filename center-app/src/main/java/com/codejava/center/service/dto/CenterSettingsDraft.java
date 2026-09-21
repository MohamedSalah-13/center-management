package com.codejava.center.service.dto;

import com.codejava.center.core.backup.BackupFrequency;
import com.codejava.center.domain.enums.Currency;
import com.codejava.center.domain.enums.NotificationChannel;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ما يُطلب حفظه من إعدادات السنتر: ما تملكه شاشةُ الإعدادات، ولا شيء غيره.
 *
 * <p>نفس قاعدة {@link UserDraft} و{@link StudentDraft}، و<b>هنا أوضح ما تكون</b>: صفُّ
 * الإعدادات واحدٌ في القاعدة كلها، و{@code save} كان يكتبه بأكمله. فكلُّ عمودٍ لا يحمله
 * المُرسِل يصير {@code null} - لا "بلا تغيير".</p>
 *
 * <p><b>ثلاثة أعمدة ليست هنا، وغيابُها هو الحماية:</b></p>
 *
 * <ul>
 *   <li>{@code id} - ثابتٌ عند واحد، فصفُّ الإعدادات واحد.</li>
 *   <li>{@code lastAutoBackupAt} و{@code lastAlertScanAt} - يكتبهما المجدوِلان بعد كل
 *       تنفيذٍ ناجح، وهما الدليل الوحيد على أن النسخ والفحص يجريان أصلاً. محوُهما لا
 *       يُعطِّل شيئاً في الحال، فلا يُكتشف إلا يوم يُطلب الدليل.</li>
 *   <li>{@code alertsEnabled} و{@code alertScanTime} - <b>ليست لشاشة الإعدادات</b>.
 *       من يملكهما هو مركز التنبيهات، وبابُهما {@code saveAlertScan}.</li>
 * </ul>
 *
 * <p>والقاعدة التي تجمع الثلاثة واحدة: <b>حقلٌ يدخل المسودة إن كانت الشاشةُ التي ترسلها
 * تملكه</b>. وشاشةُ الإعدادات على الجهاز لا تحمل حقلَ تنبيهاتٍ واحداً، ومع ذلك كانت
 * تبني {@code CenterSettings} كاملاً وتحفظه - فكلُّ ضغطةِ "حفظ" فيها كانت تُطفئ
 * التنبيهات وتمحو تاريخ آخر فحص. لا رسالةَ خطأ ولا شيء على الشاشة يقول ذلك، والنتيجة
 * "لم يصلني أي تنبيه" بعد أسابيع.</p>
 *
 * @param centerName   اسم السنتر كما يُطبع في ترويسة كل ورقة
 * @param ledgerStartDate تاريخ بداية دفتر الحسابات: تبديله يغيّر رصيد كل طالب بلا أن
 *                        يمسّ حركةً واحدة، ولذلك يُكتب في سجل المراقبة
 */
public record CenterSettingsDraft(
        @Size(max = 255) String centerName,
        @Size(max = 255) String centerPhone,
        @Size(max = 255) String logoPath,
        @Size(max = 255) String backupPath,
        boolean autoBackupEnabled,

        Currency currency,

        BackupFrequency backupFrequency,
        LocalTime backupTime,
        Integer backupDayOfWeek,
        Integer backupDayOfMonth,
        Integer backupRetentionCount,

        NotificationChannel notificationChannel,
        @Size(max = 500) String notificationApiUrl,
        @Size(max = 100) String notificationSenderId,
        @Size(max = 100) String notificationTemplateName,
        @Size(max = 20) String notificationTemplateLanguage,
        @Size(max = 500) String notificationBodyTemplate,

        LocalDate ledgerStartDate) {
}
