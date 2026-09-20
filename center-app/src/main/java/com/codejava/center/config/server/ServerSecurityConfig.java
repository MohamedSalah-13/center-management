package com.codejava.center.config.server;

import com.codejava.center.core.security.CurrentActor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * هوية المنفّذ على خادم: بنطاق الطلب، ومنقولةٌ إلى ما يعمل في الخلفية.
 *
 * <p>تحت المفتاح نفسه الذي يشغّل تعدّد المؤسسات، وليس مفتاحاً ثانياً: الاثنان وجهان
 * لسؤال واحد - أيخدم هذا البرنامج طلبات أم إنساناً واحداً أمام شاشة؟ مفتاحان يجب أن
 * يتطابقا دائماً هما مفتاحان سيختلفان يوماً، والنتيجة حينها هويةٌ واحدة لكل مؤسسات
 * الخادم.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled", havingValue = "true")
public class ServerSecurityConfig {

    /**
     * {@code @Primary} لأن سطح المكتب يحمل {@code UserSession} وهي {@link CurrentActor}
     * أيضاً؛ على خادمٍ لا وجود لها، والأولوية تجعل الحالتين تعملان بلا شرط في الوسط.
     */
    @Bean
    @Primary
    public CurrentActor serverCurrentActor() {
        return new ServerCurrentActor();
    }

    /**
     * أسرار الخادم من بيئة التشغيل.
     *
     * <p>{@code @Primary} للسبب نفسه: سطح المكتب يركّب جوابه من سجلّ الجهاز، والخادم
     * لا سجلّ له. ويحمل الاثنين معاً لأنهما يأتيان من مصدر واحد - كما يفعل
     * {@code DesktopMessagingPreferences} على الجهة الأخرى، ولنفس السبب.</p>
     */
    @Bean
    @Primary
    public EnvironmentSecrets environmentSecrets(org.springframework.core.env.Environment environment) {
        return new EnvironmentSecrets(environment);
    }

    /**
     * منفّذُ المهام الخلفية يحمل معه سياق الأمان.
     *
     * <p>{@code SecurityContextHolder} يحفظ السياق على الخيط، وخيوطُ المجمّع لا ترثه:
     * عملٌ يُسلَّم إلى الخلفية من داخل طلبٍ مصادَق يجد نفسه بلا هوية، فيرفضه
     * {@code RoleEnforcementAspect} بحجّة "لا جلسة" - والسبب الحقيقي أن الهوية لم
     * تُنقل. ولا يُحلّ ذلك بـ {@code INHERITABLETHREADLOCAL}: الوراثة تقع عند إنشاء
     * الخيط، ومجمّعٌ يُنشئ خيوطه مرةً عند الإقلاع يورّث سياق أوّل من استعمله.</p>
     *
     * <p><b>ولا يُلفّ {@code TaskScheduler} عن قصد.</b> النسخ الاحتياطي وفحص التنبيهات
     * يعملان بلا هوية - وهذا هو المقصود، فهما النظامُ لا مستخدم - ولفُّ المجدوِل يجعل
     * دورةَ الليلة تعمل بهوية من حفظ الإعدادات مساءً، فيُنسب إليه في سجل المراقبة ما
     * لم يفعله.</p>
     */
    @Bean
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutor delegate) {
        return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
    }
}
