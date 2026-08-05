package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * المنفذ الوحيد لـ {@link MessageSender}: يقرأ القناة المختارة عند كل إرسال ويحوّل إلى
 * {@link ChannelSender} المناسب.
 *
 * <p><b>عند كل إرسال لا عند الإقلاع.</b> كانت القناة تُختار بـ {@code @ConditionalOnProperty}
 * فيُسجَّل bean واحد ويُهمل الباقي: تغيير القناة كان يعني تعديل ملف داخل مجلد البرنامج
 * وإعادة تشغيله. القراءة هنا تجعل الاختيار من شاشة الإعدادات يسري على الرسالة التالية.</p>
 *
 * <p>يرفض الإقلاع إن كانت قناة معروضة في الشاشة بلا تنفيذ: البديل أن يختارها المستخدم
 * فتفشل كل رسالة، وهو خطأ يظهر عند العميل بينما هذا يظهر في أول اختبار.</p>
 */
@Component
public class MessageSenderRouter implements MessageSender {

    private final NotificationConfigProvider configProvider;
    private final Map<NotificationChannel, ChannelSender> senders = new EnumMap<>(NotificationChannel.class);

    public MessageSenderRouter(NotificationConfigProvider configProvider, List<ChannelSender> channelSenders) {
        this.configProvider = configProvider;
        channelSenders.forEach(sender -> senders.put(sender.channel(), sender));

        for (NotificationChannel channel : NotificationChannel.values()) {
            if (!senders.containsKey(channel)) {
                // خطأ برمجة يظهر عند الإقلاع، لا رسالة مستخدم — فلا مفتاح ترجمة له
                throw new IllegalStateException("No ChannelSender registered for " + channel);
            }
        }
    }

    @Override
    public SendResult send(String internationalPhone, String message) {
        NotificationConfig config = configProvider.current();
        ChannelSender sender = senders.get(config.channel());

        // المنع قبل المحاولة: مزوّد بلا مفتاح يردّ 401 برسالة إنجليزية لا تقول للموظف
        // ما ينقصه، والقناة تبقى تفشل رسالةً بعد رسالة على طول القائمة
        Optional<String> problem = sender.configurationProblem(config);
        return problem.map(SendResult::failed)
                .orElseGet(() -> sender.send(config, internationalPhone, message));
    }

    /** اسم القناة التي أُرسل بها فعلاً، كما يُحفظ في سجل الإشعارات */
    @Override
    public String channelName() {
        return configProvider.current().channel().name();
    }

    @Override
    public boolean requiresManualConfirmation() {
        return configProvider.current().channel().requiresManualConfirmation();
    }

    @Override
    public Optional<String> configurationProblem() {
        NotificationConfig config = configProvider.current();
        return senders.get(config.channel()).configurationProblem(config);
    }
}
