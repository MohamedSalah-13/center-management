package com.codejava.center;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * نقطةُ إقلاعٍ للاختبارات وحدها.
 *
 * <p>{@code @DataJpaTest} تبحث صعوداً في شجرة الحزم عن صنف {@code @SpringBootConfiguration}
 * لتعرف أين تمسح الكيانات والمستودعات. وكان ذلك الصنف {@code CenterApplication} — وهو
 * يستدعي {@code Application.launch} ويعيش مع الشاشات. فصلُ هذه الوحدة يعني أنها لم تعد
 * تراه، وهو <b>الغرض</b>: طبقة الأعمال تُختبر بلا تطبيق JavaFX خلفها.</p>
 *
 * <p>في نطاق الاختبار لا في {@code src/main}: هذه الوحدة مكتبةٌ تُستهلك، لا برنامجٌ
 * يُقلع. من يقلع هو {@code center-desktop} اليوم، وخادم {@code center-web} غداً.</p>
 */
@SpringBootApplication
public class AppTestApplication {
}
