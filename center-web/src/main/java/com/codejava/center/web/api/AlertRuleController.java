package com.codejava.center.web.api;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.alert.AlertService;
import com.codejava.center.service.dto.AlertRuleDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * ضبطُ أنواع التنبيهات: مفعَّلٌ أم لا، لمن يذهب، بأي حدود.
 *
 * <p>القائمة تُبنى من {@code AlertType} في كل قراءة ولا يزرع الترحيلُ صفوفاً، فنوعٌ
 * يُضاف غداً يظهر هنا بضبطه الافتراضي بلا ملفِّ ترحيلٍ خاصّ به. ولذلك المسار
 * {@code /api/alert-rules/{type}} بالثابت لا بمعرّفٍ رقمي: لا صفَّ لنوعٍ لم يُضبط بعد،
 * وأولُ حفظٍ هو ما يكتبه.</p>
 *
 * <p><b>وكلُّ نوعٍ يصل {@code INTERNAL} في تركيبٍ جديد.</b> ترقيةٌ يجب ألا تجعل
 * البرنامج يبدأ بمراسلة أولياء الأمور - عن المال، بلا أن يطلب أحد - فالوجهة قرارُ
 * صاحب السنتر، ويُكتب في سجل المراقبة. والحافة لا تلطّف ذلك: نوعٌ لا يصلح للإرسال
 * تُردّ وجهتُه إلى الداخل في الخدمة، فلا يحفظ الطلبُ وعداً لا يتحقق.</p>
 *
 * <p>ولأن الشاشة لا تكتب نصّاً عربياً، يحمل كلُّ صفّ <b>ما يصفه</b> معه: اسمُ النوع
 * ووصفُه، وعنوانا الحدّ والنافذة حين يستعملهما، وهل يقبل الإرسالَ إلى ولي الأمر
 * أصلاً - وهو ما يقرّر أن خيار الوجهة يُعرض أم لا.</p>
 */
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;

    /**
     * @param parentCapable هل يقبل هذا النوع الإرسال إلى ولي الأمر؟ الخيارُ يُعرض به
     * @param thresholdLabel عنوانُ الحدّ بلغة النوع نفسه، أو {@code null} إن لم يستعمله
     * @param scheduled نوعٌ مجدول له تهدئة؛ الحَدَثيّ يُطلق لحظة وقوعه فلا معنى لضبطها
     */
    public record RuleView(String type, String typeName, String description,
                           String category, String categoryName,
                           boolean enabled,
                           String audience, String audienceName, boolean parentCapable,
                           String severity, String severityName,
                           Integer threshold, String thresholdLabel,
                           Integer windowDays, String windowLabel,
                           Integer cooldownDays, boolean scheduled,
                           LocalDateTime updatedAt, String updatedBy) {
    }

    /** جسمُ الطلب بلا نوع: النوعُ في المسار، وهو مفتاحُ الصفّ لا حقلٌ فيه */
    public record RuleRequest(boolean enabled,
                              @NotNull AlertAudience audience,
                              @NotNull AlertSeverity severity,
                              Integer threshold,
                              Integer windowDays,
                              Integer cooldownDays) {

        AlertRuleDraft toDraft(AlertType type) {
            return new AlertRuleDraft(type, enabled, audience, severity,
                    threshold, windowDays, cooldownDays);
        }
    }

    public record OptionView(String name, String label) {
    }

    @GetMapping
    public List<RuleView> rules() {
        return alertService.getRules().stream().map(AlertRuleController::view).toList();
    }

    @GetMapping("/severities")
    public List<OptionView> severities() {
        return Arrays.stream(AlertSeverity.values())
                .map(severity -> new OptionView(severity.name(), severity.getDisplayName()))
                .toList();
    }

    @GetMapping("/audiences")
    public List<OptionView> audiences() {
        return Arrays.stream(AlertAudience.values())
                .map(audience -> new OptionView(audience.name(), audience.getDisplayName()))
                .toList();
    }

    @PutMapping("/{type}")
    public RuleView save(@PathVariable AlertType type, @Valid @RequestBody RuleRequest request) {
        return view(alertService.saveRule(request.toDraft(type)));
    }

    /**
     * الأرقام تخرج <b>محلولة</b> لا كما هي في الصفّ.
     *
     * <p>{@code null} في العمود يعني "استعمل افتراضي النوع"، وهو جوابٌ صحيحٌ لمن
     * يحسب ولا يصلح لمن يعرض: حقلٌ فارغ أمام مَن يضبط قاعدةً لا يقول ما الحدّ
     * العامل الآن، ثم يُعيده الحفظ فارغاً فلا يُقرأ ما استُبدل. وما لا يستعمله
     * النوع يخرج {@code null} ليختفي حقلُه - رقمٌ بجوار عنوانٍ فارغ لا يقول ما هو،
     * وهو نفسُ ما تفعله نافذةُ القاعدة على سطح المكتب.</p>
     */
    private static RuleView view(AlertRule rule) {
        AlertType type = rule.getType();
        boolean scheduled = type.usesThreshold() || type.usesWindow();
        return new RuleView(type.name(), type.getDisplayName(), type.getDescription(),
                type.getCategory() == null ? null : type.getCategory().name(),
                type.getCategory() == null ? null : type.getCategory().getDisplayName(),
                rule.isEnabled(),
                rule.getAudience() == null ? null : rule.getAudience().name(),
                rule.getAudience() == null ? null : rule.getAudience().getDisplayName(),
                type.isParentCapable(),
                rule.getSeverity() == null ? null : rule.getSeverity().name(),
                rule.getSeverity() == null ? null : rule.getSeverity().getDisplayName(),
                type.usesThreshold() ? rule.thresholdOrDefault() : null,
                type.usesThreshold() ? type.getThresholdLabel() : null,
                type.usesWindow() ? rule.windowDaysOrDefault() : null,
                type.usesWindow() ? type.getWindowLabel() : null,
                scheduled ? rule.cooldownDaysOrDefault() : null, scheduled,
                rule.getUpdatedAt(), rule.getUpdatedBy());
    }
}
