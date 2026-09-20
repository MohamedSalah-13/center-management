package com.codejava.center;

import com.codejava.center.config.DesktopCurrencyProvider;
import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.service.notification.ChannelSender;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.util.MoneyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * يتأكد أن سياق التطبيق كاملاً يُحمَّل: كل الشاشات والخدمات والمستودعات معاً.
 *
 * <p>كل الاختبارات الأخرى شرائح: {@code @DataJpaTest} لا يحمّل الشاشات ولا الخدمات،
 * واختبار الإشعارات يحقن بديلاً اختبارياً عن مرسل الرسائل. النتيجة أن bean ناقصاً أو
 * تبعية غير قابلة للحل لا يظهران في أي اختبار، بل عند تشغيل التطبيق فقط.</p>
 *
 * <p>حدث هذا فعلاً: {@code @ConditionalOnMissingBean} على {@code WhatsAppLinkSender}
 * كان يُقيَّم مقابل تعريف الصنف نفسه فيستبعد نفسه، فلا يُسجَّل أي {@link MessageSender}
 * ويفشل الإقلاع بـ NoSuchBeanDefinitionException. مرّ ذلك عبر 51 اختباراً.</p>
 *
 * <p>يعمل على H2 مع تعطيل Flyway، فلا يلمس قاعدة بيانات حقيقية.</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ApplicationContextSmokeTest {

    @Autowired private ApplicationContext context;

    @Test
    void contextLoadsWithEveryBeanResolvable() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    /**
     * لا يقلع التطبيق بدون قناة إرسال، لأن NotificationService يحقنها إلزامياً.
     *
     * <p>ويبقى المنفذ واحداً رغم تعدّد القنوات: {@code ChannelSender} لا يمتدّ
     * {@link MessageSender} حتى لا يصير في السياق أربعة beans من نوعه فيلتبس على
     * الحاقن أيها يأخذ - وهو الغموض نفسه الذي أوقع الإصدار السابق.</p>
     */
    @Test
    void exactlyOneMessageSenderIsRegistered() {
        assertThat(context.getBeansOfType(MessageSender.class)).hasSize(1);
    }

    /**
     * قناة معروضة في شاشة الإعدادات بلا تنفيذ يختارها المستخدم فتفشل كل رسالة.
     * {@code MessageSenderRouter} يرفض الإقلاع في هذه الحالة، وهذا الاختبار يسمّي السبب
     * حين يحدث بدل رسالة إقلاع غامضة.
     */
    @Test
    void everyNotificationChannelHasAnImplementation() {
        var channels = context.getBeansOfType(ChannelSender.class).values().stream()
                .map(ChannelSender::channel)
                .toList();

        assertThat(channels).containsExactlyInAnyOrder(NotificationChannel.values());
    }

    /**
     * مصدر العملة مركَّب فعلاً، لا مجرّد bean في السياق.
     *
     * <p>{@code MoneyUtils} ساكن وجوابه الافتراضي الجنيه - وهو ما يظهر على شاشة سنترٍ
     * مصري أصلاً. فتركيبٌ نُسي لا يُسقط إقلاعاً ولا يغيّر شيئاً على أغلب الأجهزة،
     * ويظهر وحده عند سنترٍ سعودي: كل المبالغ مذيَّلة برمز الجنيه وكل رقم فيها صحيح.
     * خطأٌ من هذا النوع لا يكتشفه إلا قارئ إيصال، وهذا السطر يسمّيه اليوم.</p>
     *
     * <p>يُفحص هنا لأن {@code ApplicationReadyEvent} يقع في هذا الاختبار كما يقع في
     * التطبيق. أما مصدر اللغة فيُركَّب في {@code JavaFxApplication.init()} خارج سياق
     * Spring، فلا يراه اختبارُ سياق.</p>
     */
    @Test
    void theCurrencySourceIsInstalledWhenTheApplicationIsReady() {
        assertThat(MoneyUtils.provider())
                .as("مصدر العملة")
                .isInstanceOf(DesktopCurrencyProvider.class);
    }

    /** كل شاشة يجب أن تكون قابلة للإنشاء: الشاشة المعطوبة لا تظهر إلا عند فتحها */
    @Test
    void everyControllerCanBeInstantiated() {
        var controllers = context.getBeansWithAnnotation(org.springframework.stereotype.Controller.class);

        assertThat(controllers).isNotEmpty();
        controllers.forEach((name, bean) -> assertThat(bean).as(name).isNotNull());
    }
}
