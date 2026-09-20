package com.codejava.center;

import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * يُشغّل AOP داخل شريحة اختبار.
 *
 * <p>{@code @DataJpaTest} شريحةٌ ضيّقة بلا وكلاء AOP: الخدمة تُستدعى مباشرةً، فيمرّ
 * اختبارُ حارسٍ أخضرَ وهو لا يعمل. وهذا شكلُ الخلل نفسه الذي وُجد الحارس ليمنعه - لكن
 * في الاختبار بدل الإنتاج، وهو أسوأ لأنه يقول إن الحدّ قائم.</p>
 *
 * <p>ولا يحمل {@code @Configuration}: صنفٌ كهذا داخل صفّ اختبار يصير هو <b>مصدر</b>
 * السياق فلا يبقى {@code AppTestApplication} مرئياً، ويسقط الإقلاع بـ "Unable to
 * retrieve @EnableAutoConfiguration base packages". و{@code @Import} عليه يكفي: ما
 * يحتاجه {@code @EnableAspectJAutoProxy} هو أن يُقرأ، لا أن يكون تهيئةً كاملة.</p>
 */
@EnableAspectJAutoProxy
public class AspectProxying {
}
