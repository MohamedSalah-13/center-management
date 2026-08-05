package com.codejava.center.service.alert;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * قواعد التنبيهات كما هي الآن: المحفوظ منها، وافتراضات الباقي.
 *
 * <p><b>الأنواع لا تُزرع في الترحيل.</b> صفٌّ لكل نوع في {@code V5} كان يعني أن كل نوع
 * يُضاف في نسخة لاحقة يحتاج ترحيله الخاص ليزرع صفّه، وأن نسيان ذلك يترك نوعاً يظهر في
 * الشاشة بلا ضبط ولا يعمل. هنا القائمة تُبنى من {@link AlertType} في كل قراءة، فالكود
 * وحده مصدرها.</p>
 *
 * <p>والافتراضات <b>لا تُكتب</b> عند القراءة. الكتابة عند القراءة كانت ستُدخل الفحص
 * المجدول في معاملة كتابة على كل جهاز في السنتر في نفس اللحظة، ولا تشتري شيئاً: قاعدة
 * لم يمسّها أحد تعمل بافتراضاتها سواءٌ أكانت في الجدول أم لا. الصف يُكتب أول مرة يحفظ
 * فيها المدير تعديلاً، وهو الوقت الذي صار للصف فيه معنى.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertRuleRegistry {

    private final AlertRuleRepository alertRuleRepository;

    /**
     * كل الأنواع بترتيب إعلانها - وهو ترتيبٌ مجمَّع بالتصنيف - محفوظُها وافتراضيُّها.
     */
    @Transactional(readOnly = true)
    public List<AlertRule> all() {
        Map<AlertType, AlertRule> stored = new EnumMap<>(AlertType.class);
        alertRuleRepository.findAll().forEach(rule -> stored.put(rule.getType(), rule));

        return java.util.Arrays.stream(AlertType.values())
                .map(type -> stored.getOrDefault(type, AlertRule.defaultsFor(type)))
                .toList();
    }

    /** قاعدة نوع بعينه، محفوظةً أو افتراضية */
    @Transactional(readOnly = true)
    public AlertRule forType(AlertType type) {
        return alertRuleRepository.findByType(type).orElseGet(() -> AlertRule.defaultsFor(type));
    }
}
