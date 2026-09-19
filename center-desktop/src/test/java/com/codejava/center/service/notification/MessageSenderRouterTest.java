package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * اختيار القناة عند كل إرسال.
 *
 * <p>كانت القناة تُختار بـ {@code @ConditionalOnProperty} أي عند الإقلاع، فتغييرها يعني
 * تعديل ملف داخل مجلد البرنامج وإعادة تشغيله. ما يُفحص هنا أن الاختيار صار يُقرأ لحظة
 * الإرسال، وأن قناة ناقصة الضبط تُوقَف قبل المحاولة لا بعد أربعين رسالة فاشلة.</p>
 */
class MessageSenderRouterTest {

    private final StubSender link = new StubSender(NotificationChannel.WHATSAPP_LINK);
    private final StubSender cloud = new StubSender(NotificationChannel.WHATSAPP_CLOUD_API);
    private final StubSender gateway = new StubSender(NotificationChannel.HTTP_GATEWAY);

    @Test
    void sendsThroughTheChannelChosenInTheSettings() {
        MessageSenderRouter router = routerFor(NotificationChannel.WHATSAPP_CLOUD_API);

        assertThat(router.send("201012345678", "x").success()).isTrue();
        assertThat(cloud.sentTo).containsExactly("201012345678");
        assertThat(link.sentTo).isEmpty();
    }

    /** تغيير القناة من الشاشة يجب أن يسري على الرسالة التالية بلا إعادة تشغيل */
    @Test
    void readsTheChannelAgainOnEverySend() {
        MutableProvider provider = new MutableProvider(NotificationChannel.WHATSAPP_LINK);
        MessageSenderRouter router = new MessageSenderRouter(provider, List.of(link, cloud, gateway));

        router.send("201012345678", "x");
        provider.channel = NotificationChannel.HTTP_GATEWAY;
        router.send("201012345679", "x");

        assertThat(link.sentTo).containsExactly("201012345678");
        assertThat(gateway.sentTo).containsExactly("201012345679");
    }

    /** مزوّد بلا مفتاح يردّ 401 برسالة لا تقول للموظف ما ينقصه */
    @Test
    void refusesToSendThroughAChannelThatIsNotConfigured() {
        cloud.problem = "مفتاح الدخول غير مضبوط";
        MessageSenderRouter router = routerFor(NotificationChannel.WHATSAPP_CLOUD_API);

        MessageSender.SendResult result = router.send("201012345678", "x");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("مفتاح الدخول غير مضبوط");
        assertThat(cloud.sentTo).isEmpty();
        assertThat(router.configurationProblem()).contains("مفتاح الدخول غير مضبوط");
    }

    /** اسم القناة يُحفظ في سجل الإشعارات، فيبقى السجل مفهوماً بعد تغيير المزوّد */
    @Test
    void reportsTheActiveChannelForTheNotificationLog() {
        assertThat(routerFor(NotificationChannel.HTTP_GATEWAY).channelName())
                .isEqualTo(NotificationChannel.HTTP_GATEWAY.name());
    }

    @Test
    void manualConfirmationFollowsTheChosenChannel() {
        assertThat(routerFor(NotificationChannel.WHATSAPP_LINK).requiresManualConfirmation()).isTrue();
        assertThat(routerFor(NotificationChannel.WHATSAPP_CLOUD_API).requiresManualConfirmation()).isFalse();
    }

    /**
     * قناة معروضة في الشاشة بلا تنفيذ تعني أن المستخدم يختارها فتفشل كل رسالة.
     * الإقلاع يتوقف بدلاً من ذلك، فيظهر النقص عند أول اختبار لا عند العميل.
     */
    @Test
    void refusesToStartWhenAChannelHasNoImplementation() {
        assertThatIllegalStateException().isThrownBy(() -> new MessageSenderRouter(
                new MutableProvider(NotificationChannel.WHATSAPP_LINK), List.of(link)));
    }

    private MessageSenderRouter routerFor(NotificationChannel channel) {
        return new MessageSenderRouter(new MutableProvider(channel), List.of(link, cloud, gateway));
    }

    /** يعطي القناة المطلوبة بلا قاعدة بيانات ولا تفضيلات جهاز */
    private static class MutableProvider extends NotificationConfigProvider {
        private NotificationChannel channel;

        MutableProvider(NotificationChannel channel) {
            super(null);
            this.channel = channel;
        }

        @Override
        public NotificationConfig current() {
            return new NotificationConfig(channel, WhatsAppLinkStyle.WA_ME, null,
                    null, null, null, null, null, null);
        }
    }

    private static class StubSender implements ChannelSender {
        private final NotificationChannel channel;
        private final List<String> sentTo = new java.util.ArrayList<>();
        private String problem;

        StubSender(NotificationChannel channel) {
            this.channel = channel;
        }

        @Override
        public NotificationChannel channel() {
            return channel;
        }

        @Override
        public MessageSender.SendResult send(NotificationConfig config, String phone, String message) {
            sentTo.add(phone);
            return MessageSender.SendResult.ok();
        }

        @Override
        public Optional<String> configurationProblem(NotificationConfig config) {
            return Optional.ofNullable(problem);
        }
    }
}
