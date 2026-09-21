package com.codejava.center.service.alert;

import com.codejava.center.core.alert.AlertSchedule;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.domain.Alert;
import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertCategory;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.repository.AlertRuleRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.AlertRuleDraft;
import com.codejava.center.service.AuditService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.dto.AlertPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * واجهة شاشة التنبيهات: قراءة الصندوق، ومعالجة سطوره، وضبط القواعد.
 *
 * <p><b>هنا الحارس، لا في {@link AlertEngine}.</b> المحرّك يعمل من خيط مجدوِل بلا جلسة
 * مستخدم، فحارسٌ عليه يعني رفض كل فحص ليلي. الحدّ الحقيقي موضعه هذا الصنف: من يقرأ
 * الصندوق - وفيه أرصدة الطلاب ومستحقات المعلمين ومحاولات الدخول - ومن يقرّر أن
 * البرنامج صار يراسل أولياء الأمور. الآلة تنفّذ ما قرّره المدير من هنا.</p>
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    /**
     * سقف ما تجلبه الشاشة دفعةً واحدة، تماماً كسجل المراقبة: سنتر بعد سنتين قد يحمل
     * عشرات الآلاف من التنبيهات، وجلبها كلها إلى جدول JavaFX يعلّق الواجهة.
     */
    public static final int MAX_ROWS = 500;

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleRegistry ruleRegistry;
    private final AlertEngine alertEngine;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final CurrentActor currentActor;

    // ------------------------------------------------------------------ الصندوق

    /**
     * تنبيهات فترة، مصفّاة اختيارياً بالتصنيف وبالدرجة.
     *
     * @param category التصنيف، أو {@code null} للجميع
     * @param severity الدرجة، أو {@code null} للجميع
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public AlertPage search(LocalDate from, LocalDate to, AlertCategory category, AlertSeverity severity) {
        LocalDateTime start = from.atStartOfDay();
        // بداية اليوم التالي مع مقارنة "أصغر من": تشمل آخر ثانية بكل أجزائها
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<AlertType> types = category == null ? List.of(AlertType.values()) : AlertType.of(category);
        List<AlertSeverity> severities = severity == null
                ? List.of(AlertSeverity.values()) : List.of(severity);

        // تصنيف بلا أنواع مستحيل اليوم، لكن IN على قائمة فارغة خطأ نحوي في بعض
        // اللهجات: الحارس هنا أهون من شاشة تنكسر يوم يُضاف تصنيف قبل نوعه
        if (types.isEmpty()) {
            return new AlertPage(List.of(), 0);
        }

        List<Alert> rows = alertRepository.search(start, end, types, severities,
                PageRequest.of(0, MAX_ROWS));

        return new AlertPage(rows, alertRepository.countMatching(start, end, types, severities));
    }

    /** عدد ما لم يُعالَج، لشارة القائمة الجانبية */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public long unacknowledgedCount() {
        return alertRepository.countByAcknowledgedAtIsNull();
    }

    /**
     * وضع علامة "تمت المعالجة" على تنبيهات.
     *
     * <p>لا تُحذف الصفوف: "متى انتهت هذه المشكلة، ومن نظر فيها" سؤالٌ يُطرح بعد أسبوع،
     * والحذف يجعل جوابه مستحيلاً. والعلامة لا تُنقض - التنبيه المعالَج يبقى معالَجاً،
     * وإن عادت الحالة أطلق الفحص تنبيهاً جديداً بتاريخه.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public int acknowledge(List<Long> alertIds) {
        ActorIdentity actor = currentActor.currentActor();
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;

        for (Alert alert : alertRepository.findAllById(alertIds)) {
            if (alert.isAcknowledged()) {
                continue;
            }
            alert.setAcknowledgedAt(now);
            alert.setAcknowledgedBy(actor == null ? null : actor.username());
            alertRepository.save(alert);
            changed++;
        }

        return changed;
    }

    // ------------------------------------------------------------------ القواعد

    /** كل الأنواع بضبطها الحالي، محفوظاً أو افتراضياً */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<AlertRule> getRules() {
        return ruleRegistry.all();
    }

    /**
     * حفظ ضبط قاعدة.
     *
     * <p>يُفرض {@code type.isParentCapable()} هنا لا في الشاشة وحدها: إخفاء خيارٍ في
     * قائمة منسدلة عرضٌ، والحدّ الحقيقي في طبقة الخدمات - وهي نفس القاعدة التي يقوم
     * عليها إخفاء أزرار القائمة الجانبية.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public AlertRule saveRule(AlertRuleDraft draft) {
        AlertType type = draft.type();

        AlertRule stored = alertRuleRepository.findByType(type)
                .orElseGet(() -> AlertRule.defaultsFor(type));

        stored.setEnabled(draft.enabled());
        stored.setSeverity(draft.severity());
        stored.setThreshold(draft.threshold());
        stored.setWindowDays(draft.windowDays());
        stored.setCooldownDays(draft.cooldownDays());

        // وجهةٌ إلى ولي الأمر على نوع لا يصلح للإرسال تُردّ إلى الداخل بدل أن تُحفظ
        // فتبقى وعداً لا يتحقق: فشل نسخة احتياطية لا يُرسل إلى هاتف أحد
        stored.setAudience(type.isParentCapable() ? draft.audience() : AlertAudience.INTERNAL);

        ActorIdentity actor = currentActor.currentActor();
        stored.setUpdatedAt(LocalDateTime.now());
        stored.setUpdatedBy(actor == null ? null : actor.username());

        AlertRule saved = alertRuleRepository.save(stored);

        auditService.record(AuditAction.ALERT_RULE_UPDATED, saved.getId(), type.name(),
                "enabled=" + saved.isEnabled()
                        + "; audience=" + saved.getAudience()
                        + "; severity=" + saved.getSeverity()
                        + "; threshold=" + saved.getThreshold()
                        + "; windowDays=" + saved.getWindowDays()
                        + "; cooldownDays=" + saved.getCooldownDays());

        return saved;
    }

    // -------------------------------------------------------------------- الفحص

    /**
     * فحصٌ فوريّ بطلب المدير.
     *
     * <p>يُسجَّل وقته كما يُسجَّل الفحص المجدول: التعويض عن موعد فائت يقارن بآخر فحص
     * ناجح أياً كان مصدره، ولو لم يُسجَّل الفحص اليدوي لَأعاد المجدوِل الفحص نفسه بعد
     * دقائق من ضغط المستخدم على الزر.</p>
     */
    @RequiresRole(Role.ADMIN)
    public AlertScanResult scanNow() {
        AlertScanResult result = alertEngine.scanAll();
        settingsService.recordAlertScanAt(LocalDateTime.now());
        return result;
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public ScanSettings getScanSettings() {
        CenterSettings settings = settingsService.getSettings();
        return new ScanSettings(settings.isAlertsEnabled(),
                AlertSchedules.from(settings).time(), settings.getLastAlertScanAt());
    }

    /**
     * حفظ المفتاح الرئيسي وموعد الفحص.
     *
     * <p>يمرّ عبر {@code SettingsService.save} لا عبر كتابة مباشرة، فينشر
     * {@code SettingsChangedEvent} بعد الإيداع ويعيد {@code AlertScheduler} جدولة
     * نفسه فوراً. بدون ذلك يبقى الموعد القديم عاملاً حتى إعادة تشغيل البرنامج، وهو
     * بالضبط ما جعل {@code @Scheduled} الثابت غير صالح.</p>
     */
    @Transactional
    @RequiresRole(Role.ADMIN)
    public void saveScanSettings(boolean enabled, LocalTime time) {
        // بابُ الحقلين وحدهما، لا الصفُّ كاملاً: شاشتان تكتبان في صفِّ إعداداتٍ واحد،
        // وكتابةُ الصفِّ كلِّه من إحداهما تمحو ما ضبطته الأخرى
        settingsService.saveAlertScan(enabled, time == null ? AlertSchedule.DEFAULT_TIME : time);
    }

    /**
     * @param lastScanAt آخر فحص ناجح، أو {@code null} إن لم يقع فحص بعد - وهو ما
     *                   يجعل فشلاً متكرراً مرئياً في الشاشة بدل أن يمرّ بصمت
     */
    public record ScanSettings(boolean enabled, LocalTime time, LocalDateTime lastScanAt) {
    }
}
