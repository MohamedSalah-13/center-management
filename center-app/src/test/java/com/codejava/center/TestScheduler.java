package com.codejava.center;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * مجدوِلُ المهام في الاختبارات: ما يحقنه {@code BackupScheduler} و{@code AlertScheduler}.
 *
 * <p>لا تملكه هذه الوحدة لأنها مكتبة: البرنامجان يكتبان {@code @EnableScheduling}
 * فيصنعه Boot لهما - {@code CenterApplication} على الجهاز و{@code CenterWebApplication}
 * على خادم. فحين تُقلع الاختبارات سياقاً كاملاً بلا برنامج خلفه لا يوجد من يصنعه،
 * ويسقط الإقلاع بـ "required a bean of type TaskScheduler". نفس سبب وجود
 * {@link TestActor} و{@link TestPorts}: منفذٌ جوابه عند الطرف، فتجيب عنه الاختبارات.</p>
 *
 * <p><b>{@code @Component} لا {@code @Bean} على {@link AppTestApplication}</b>، وهذا
 * ليس ذوقاً: شرائح {@code @DataJpaTest} تُسجّل دوالّ {@code @Bean} الخاصة بصنف الإقلاع
 * ولا تمسح {@code @Component}. فـ bean هناك يعني مجمّع خيوط في كل شريحة، ويعني تصادماً
 * بالاسم مع شريحةٍ تصنع مجدوِلها بنفسها - و{@code AlertFeedTest} تفعل ذلك لأنها تفحص
 * النبضة، فتحتاج مجدوِلاً تتحكّم فيه لا مجدوِلاً يعمل بالساعة.</p>
 *
 * <p>ولا {@code @EnableScheduling} في أيّ منهما: المطلوب هو ما يُحقن، وتشغيلُ معالجة
 * {@code @Scheduled} في الاختبارات يعني مؤقّتات تعمل في اختبارات لا تعني بها.</p>
 */
@Component
public class TestScheduler extends ThreadPoolTaskScheduler implements TaskScheduler {

    public TestScheduler() {
        setPoolSize(1);
        setThreadNamePrefix("test-scheduler-");
        // خيط خفيّ: اختبارٌ ينتهي ومجدوِلٌ حيّ يمنع الـ JVM من الخروج
        setDaemon(true);
    }
}
