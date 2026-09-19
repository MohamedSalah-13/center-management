package com.codejava.center.service.alert;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.util.I18n;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * موعد الفحص التلقائي التالي للتنبيهات.
 *
 * <p>صنف خالٍ من Spring ومن JavaFX عن قصد، تماماً كـ {@code BackupSchedule}: هذا هو
 * الحساب الذي إن أخطأ لم يظهر تنبيه واحد لأشهر بينما تبدو كل القواعد مفعَّلة في
 * الشاشة، و{@code AlertScheduleTest} يغطّيه بلا سياق تطبيق ولا قاعدة بيانات.</p>
 *
 * <p>الفحص يومي بلا خيار أسبوعي أو شهري - على عكس النسخ الاحتياطي. تنبيهٌ عن حصة
 * بقيت مفتوحة أمس يصل بعد أسبوع ليس تنبيهاً بل خبراً، والاختيار الوحيد الذي يعني
 * شيئاً هو ساعة اليوم.</p>
 */
public record AlertSchedule(LocalTime time) {

    /**
     * الثامنة صباحاً: قبل أن يفتح السنتر أبوابه بساعات، وبعد أن انتهت ليلة النسخ
     * الاحتياطي فلا يتزاحم الفحص معها على جهاز واحد ضعيف.
     */
    public static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);

    public static AlertSchedule from(CenterSettings settings) {
        LocalTime configured = settings.getAlertScanTime();
        return new AlertSchedule(configured == null ? DEFAULT_TIME : configured);
    }

    /**
     * أول موعد فحص يقع <b>بعد</b> اللحظة المعطاة تماماً.
     *
     * <p>"بعد" لا "بعد أو يساوي": الدالة تُستدعى بعد كل تنفيذ لتحديد التالي، ولو قبلت
     * المساواة لأعادت اللحظة نفسها ودار الفحص في حلقة.</p>
     */
    public LocalDateTime nextRunAfter(LocalDateTime reference) {
        LocalDate today = reference.toLocalDate();
        LocalDateTime at = today.atTime(time);
        return at.isAfter(reference) ? at : today.plusDays(1).atTime(time);
    }

    /**
     * هل فات موعدٌ كان يجب أن يقع فيه فحص؟
     *
     * <p>هو ما يجعل الميزة تعمل أصلاً، تماماً كنظيره في النسخ الاحتياطي: جهاز السنتر
     * قد يُفتح الساعة الثانية ظهراً، فلو انتظر المجدوِل الثامنة القادمة لَما وقع فحص
     * في ذلك اليوم أبداً - والتنبيهات التي فاتت هي بالضبط ما يريد صاحب السنتر أن
     * يعرفه لحظة فتحه للبرنامج.</p>
     *
     * @param lastRun آخر فحص ناجح، أو {@code null} إن لم يقع فحص بعد
     */
    public boolean isOverdue(LocalDateTime lastRun, LocalDateTime now) {
        return lastRun == null || !nextRunAfter(lastRun).isAfter(now);
    }

    /** وصف الموعد بلغة الواجهة، لعرضه في شاشة إدارة التنبيهات */
    public String describe() {
        return I18n.format("alerts.scheduleDaily",
                String.format("%02d:%02d", time.getHour(), time.getMinute()));
    }
}
