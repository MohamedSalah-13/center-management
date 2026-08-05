package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import com.codejava.center.service.alert.AlertRuleRegistry;
import com.codejava.center.service.notification.MessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * قناة إرسال الرسائل غير جاهزة بينما هناك قاعدة تعتمد عليها.
 *
 * <p>الشرطان معاً لا أحدهما. قناة غير مضبوطة على سنتر لا يراسل أولياء الأمور أصلاً
 * ليست عطلاً بل اختياراً، والتنبيه عليها يومياً يعلّم المستخدم أن يتجاهل الصندوق.
 * أما قاعدة مفعَّلة وجهتها ولي الأمر مع قناة معطوبة فهي أسوأ الحالات: النظام يظنّ أنه
 * يُبلّغ ولا شيء يصل، ولا أحد يكتشف ذلك لأن الفشل لا يقع أمام أحد.</p>
 *
 * <p>ولا يُخزَّن سبب العطل في التنبيه رغم أن {@code configurationProblem} يعرفه: نصّه
 * مترجَم بلغة الجهاز الذي فحص، وتخزينه يجمّد السطر على تلك اللغة. السبب التفصيلي
 * مكانه شاشة الإعدادات، وهي تعرضه بلغة من ينظر إليها.</p>
 */
@Component
@RequiredArgsConstructor
public class MessagingChannelDetector implements AlertDetector {

    private final MessageSender messageSender;
    private final AlertRuleRegistry ruleRegistry;

    @Override
    public AlertType type() {
        return AlertType.MESSAGING_CHANNEL_DOWN;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        boolean anyRuleMessagesParents = ruleRegistry.all().stream().anyMatch(AlertRule::notifiesParents);
        if (!anyRuleMessagesParents) {
            return List.of();
        }

        return messageSender.configurationProblem().isEmpty()
                ? List.of()
                : List.of(AlertDraft.internal(null, null));
    }
}
