package com.codejava.center.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * ساعةُ البرنامج، bean واحد يُحقن بدل {@code LocalDateTime.now()} المباشر.
 *
 * <p><b>لماذا bean وليس استدعاءً ساكناً:</b> {@code now()} يقرأ ساعة الجهاز ومنطقته من
 * داخل الدالة، فلا سبيل إلى تحريك الوقت في اختبار. والحسابات التي تعتمد عليه هنا ليست
 * تفصيلاً: "هل فات موعد النسخة" و"صافي درج اليوم" و"آخر ثلاثين يوماً" — وكلّها يُكتشف
 * خطؤها عند العميل لأن الاختبار لا يستطيع أن يقف على حافة منتصف الليل.</p>
 *
 * <p><b>والمنطقة الزمنية نصف المسألة.</b> على Desktop منطقةُ الجهاز هي منطقةُ السنتر:
 * الجهاز في القاهرة والسنتر في القاهرة. وعلى خادم يخدم سناتر في مناطق مختلفة لا يصحّ ذلك
 * — "اليوم" هناك يختلف بين مستأجر وآخر، والخادم نفسه يعمل غالباً بـ UTC. حين يأتي ذلك
 * اليوم تُستبدل هذه الـ bean بساعة لكل مؤسسة ({@link Clock#withZone(ZoneId)})، ولا يتغيّر
 * سطر واحد في الخدمات.</p>
 *
 * <p>التحويل <b>تدريجي</b> كما تقول الخطة: المجدولان و{@code TransactionService} أولاً،
 * وهي المواضع التي يكون فيها "الآن" قراراً لا مجرّد ختم زمني. راجع
 * {@code docs/saas-review-and-plan.md} البند 10.</p>
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
