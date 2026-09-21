package com.codejava.center.platform;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * مسحٌ واحد يقول حالَ كل سنترٍ على هذه المنصة.
 *
 * <p>هو الجواب عن السؤال الذي لم يكن له جواب: مجدوِلُ النسخ يعمل لكل مؤسسة ويكتب
 * {@code lastAutoBackupAt} في قاعدتها، ومحرّكُ التنبيهات كذلك - فكلُّ سنترٍ يعرف حالَ
 * نفسه، ولا أحد يعرف حالَ الخمسين. وفشلُ الدورة يُسجَّل باسم المؤسسة
 * ({@link ServerTenantContext#sweep}) وهو سجلٌّ يُقرأ بعد أن يسأل أحدٌ، والسؤال هو ما
 * لا يقع.</p>
 *
 * <h2>ولا شيء هنا يمرّ بخدمةٍ محروسة</h2>
 *
 * <p>مشغّلُ المنصة <b>ليس مستخدماً في أي سنتر</b>: لا صفَّ له في {@code users} ولا
 * جلسةَ سنترٍ تحمله - رمزُ التشغيل هو هويّته كلها. فـ{@code AlertService.unacknowledgedCount}
 * وهي {@code @RequiresRole(ADMIN)} تَرفضه في كل سنترٍ يمرّ به، ومسحٌ يُبنى عليها يعود
 * خمسين رفضاً. القراءةُ هنا من المستودع مباشرةً، لنفس السبب الذي جعل
 * {@code BackupService.executeBackup} بلا حارس: خيطٌ بلا جلسة يؤدّي عملاً حقيقياً.</p>
 *
 * <p>و{@code SettingsService.getSettings} تُستدعى كما هي لأنها قراءةٌ بلا حارس، وهي
 * الموضع الوحيد الذي يعرف أن الإعدادات صفٌّ واحد رقمُه واحد.</p>
 *
 * <h2>والفشل معزولٌ لكل سنتر، ويبقى في القائمة</h2>
 *
 * <p>قاعدةٌ لا تُفتح أو مخطَّطٌ لم يُرحَّل يُسقط قراءةَ سنترٍ واحد. ابتلاعُه وإسقاطُ
 * السطر يترك تسعةً وأربعين سطراً سليماً وقائمةً تبدو تامّة - والسنترُ الغائب هو
 * بالضبط الذي كان ينبغي أن يُرى. نفسُ حجّة {@code sweep}، ونفسُ حجّة سقف الصفوف في
 * سجلّ المراقبة.</p>
 */
public class PlatformOperations {

    private static final Logger log = LoggerFactory.getLogger(PlatformOperations.class);

    private final TenantRegistry registry;
    private final ServerTenantContext tenantContext;
    private final SettingsService settingsService;
    private final AlertRepository alertRepository;
    private final Clock clock;

    public PlatformOperations(TenantRegistry registry, ServerTenantContext tenantContext,
                              SettingsService settingsService, AlertRepository alertRepository,
                              Clock clock) {
        this.registry = registry;
        this.tenantContext = tenantContext;
        this.settingsService = settingsService;
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    /**
     * حالُ كل سنترٍ في السجلّ، بترتيبه.
     *
     * <p>{@code all()} لا {@code served()}: السنترُ الموقوف جزءٌ من جواب "لماذا هذا
     * مظلم" - وحذفُه من المسح يجعل غيابَه لغزاً بدل أن يكون سطراً يقول "موقوف".</p>
     */
    public List<CentreOperations> survey() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CentreOperations> rows = new ArrayList<>();

        for (PlatformTenant tenant : registry.all()) {
            try {
                rows.add(read(tenant, now));
            } catch (RuntimeException e) {
                // السطر يبقى موسوماً بسببه: إسقاطُه يترك تسعةً وأربعين سطراً سليماً
                // وقائمةً تبدو تامّة، والغائبُ هو الذي كان ينبغي أن يُرى
                log.error("تعذّر مسحُ المؤسسة {} ({}): {}",
                        tenant.slug(), tenant.id().value(), e.getMessage(), e);
                rows.add(CentreOperations.unreadable(tenant, e.getMessage()));
            }
        }
        return rows;
    }

    /**
     * قراءةُ سنترٍ واحد داخل نطاقه، ترمي إن تعذّرت.
     *
     * <p>العزلُ في {@link #survey} لا هنا، وذلك يقسم المسؤوليتين حيث تُختبران: هذه
     * تقرأ، وتلك تقرّر ما يحدث حين لا تُقرأ. و{@code package-private} ليحلّ الاختبارُ
     * محلَّها، إذ المسارُ الحقيقي يحتاج MySQL بمخطَّطين وDocker بينما المقصود سلوكُ
     * الحلقة - نفسُ حجّة البديل عن مجمّع الاتصالات في {@code SchemaRoutingTest}.</p>
     */
    CentreOperations read(PlatformTenant tenant, LocalDateTime now) {
        return tenantContext.call(tenant.id(), () -> CentreOperations.of(tenant,
                settingsService.getSettings(),
                alertRepository.countBySeverityAndAcknowledgedAtIsNull(AlertSeverity.CRITICAL),
                now));
    }
}
