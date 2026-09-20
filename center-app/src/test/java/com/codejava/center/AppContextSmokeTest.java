package com.codejava.center;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * سياق هذه الوحدة كاملاً يُحمَّل، بلا Docker وبلا قاعدة حقيقية.
 *
 * <p>كان {@code MySqlMigrationIntegrationTest} هو الاختبار الوحيد الذي يُقلع سياق
 * {@code center-app} كاملاً - وهو موقوفٌ بلا Docker. والنتيجة أن bean ناقصاً في هذه
 * الوحدة لا يظهر على جهاز مطوّر إطلاقاً، بل في CI وحده، وبعد بناء يستغرق دقيقتين.</p>
 *
 * <p>ووقع ذلك فعلاً: {@code BackupScheduler} صار يحقن {@code TaskScheduler} حين صارت
 * الجدولة لكل مؤسسة، ولا أحد يصنعه هنا - البرنامجان يكتبان {@code @EnableScheduling}
 * فيصنعه Boot لهما، وهذه الوحدة مكتبة لا برنامج. فسقط الإقلاع بـ "required a bean of
 * type TaskScheduler"، ومرّ ذلك عبر المجموعة كلها خضراء.</p>
 *
 * <p>وهذا هو نظير {@code ApplicationContextSmokeTest} على الجهاز
 * و{@code WebContextSmokeTest} على الخادم: ثلاثتها تسأل السؤال نفسه عن ثلاثة
 * تركيبات.</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AppContextSmokeTest {

    @Autowired private ApplicationContext context;

    @Test
    void everyBeanInTheBusinessLayerResolves() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}
