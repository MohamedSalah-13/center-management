package com.codejava.center.web;

import com.codejava.center.security.RoleEnforcementAspect;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * العملُ الخلفي يحمل معه هويةَ من طلبه.
 *
 * <p>{@code SecurityContextHolder} يحفظ السياق على الخيط، وخيوطُ المجمّع لا ترثه: عملٌ
 * يُسلَّم إلى الخلفية من داخل طلبٍ مصادَق يجد نفسه بلا هوية، فيرفضه
 * {@link RoleEnforcementAspect} بحجّة "لا جلسة" - والسبب الحقيقي أن الهوية لم تُنقل.
 * ولا يُحلّ ذلك بـ {@code INHERITABLETHREADLOCAL}: الوراثة تقع عند إنشاء الخيط،
 * ومجمّعٌ يُنشئ خيوطه مرةً عند الإقلاع يورّث سياق أوّل من استعمله.</p>
 *
 * <p><b>ولا يُلفّ {@code TaskScheduler} عن قصد.</b> النسخ الاحتياطي وفحص التنبيهات
 * يعملان بلا هوية - وهذا هو المقصود، فهما النظامُ لا مستخدم، ولذلك
 * {@code BackupService.executeBackup} بلا حارس أصلاً - ولفُّ المجدوِل يجعل دورةَ الليلة
 * تعمل بهوية من حفظ الإعدادات مساءً، فيُنسب إليه في سجل المراقبة ما لم يفعله.</p>
 *
 * <p>وهو هنا لا تحت مفتاح تعدّد المؤسسات كما كان: خادمٌ لسنترٍ واحد يحتاجه تماماً كما
 * يحتاجه خادم منصة - المشكلة في خيوط المجمّع لا في عدد السناتر.</p>
 */
@Configuration
public class WebAsyncConfig {

    /**
     * المنفّذ الحقيقي، bean قائم بذاته.
     *
     * <p>ولا يُبنى داخل الدالة التالية: {@link ThreadPoolTaskExecutor} يحتاج
     * {@code initialize()} عند البدء و{@code shutdown()} عند الإغلاق، وكلاهما يقع
     * لأنه bean. كائنٌ يُبنى داخل دالةٍ ويُلفّ لا يمرّ بأيٍّ منهما - فيُقلع بمجمّعٍ
     * غير مهيّأ، وتبقى خيوطه عند الإغلاق تمنع الـ JVM من الخروج.</p>
     *
     * <p>و{@code TaskExecutorBuilder} هو ما يبنيه Boot بنفسه من
     * {@code spring.task.execution.*}، فتبقى إعدادات حجم المجمّع في مكانها المعتاد.</p>
     */
    @Bean
    public ThreadPoolTaskExecutor securedTaskExecutorDelegate(TaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * الاسم {@code applicationTaskExecutor} مقصود: هو الاسم الذي يبحث عنه Spring MVC
     * لتنفيذ الطلبات غير المتزامنة، وbean بلفٍّ صحيح باسمٍ آخر لا يُستعمل أبداً.
     */
    @Bean
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutor securedTaskExecutorDelegate) {
        return new DelegatingSecurityContextAsyncTaskExecutor(securedTaskExecutorDelegate);
    }
}
