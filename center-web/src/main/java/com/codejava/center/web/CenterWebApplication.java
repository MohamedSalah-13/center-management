package com.codejava.center.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * خادم السنتر على الويب: نفس خدمات {@code center-app} بواجهة HTTP.
 *
 * <p>لا منطق أعمال في هذه الوحدة. ما فيها هو ما يخصّ الطلب وحده: من صاحبه، وأيّ مؤسسة
 * يعمل لأجلها، وكيف تتحوّل نتيجةُ خدمةٍ إلى JSON أو PDF أو تيار. كلُّ قرارٍ يخصّ السنتر
 * - رصيدٌ، حضورٌ، تنبيهٌ - يبقى حيث هو، ويعمل هنا كما يعمل على الجهاز.</p>
 *
 * <p>النطاقات مذكورة صراحةً لأن صنف الإقلاع خارج حزمة الأعمال: {@code @SpringBootApplication}
 * يمسح حزمته وما تحتها، وطبقة الأعمال في وحدة أخرى تحت {@code com.codejava.center}.
 * وتركُها للاستنتاج يعني سياقاً يُقلع بلا مستودعات وبلا كيانات، ورسالةَ خطأ عن bean
 * مفقود لا عن نطاق ناقص.</p>
 */
/*
 * UserDetailsServiceAutoConfiguration مستبعدة: هي تسجّل مستخدماً في الذاكرة بكلمة
 * مرور عشوائية تُطبع في السجل عند كل إقلاع. لا مسار هنا يستعملها - المصادقة تقع في
 * SessionController عبر AuthService - وسطرٌ يعلن كلمة مرور في سجلّ خادم يُقرأ على
 * أنه حسابُ دخولٍ قائم، وهو ما يدفع من يقرؤه إلى البحث عن ثغرة لا وجود لها... أو
 * إلى تجاهل السطر التالي الذي يهمّ.
 */
@SpringBootApplication(scanBasePackages = "com.codejava.center",
        exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan("com.codejava.center.domain")
@EnableJpaRepositories("com.codejava.center.repository")
// بدونها لا يُنشئ Spring مشغّل المهام الذي يحقنه BackupScheduler، فلا تعمل أي نسخة تلقائية
@EnableScheduling
public class CenterWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CenterWebApplication.class, args);
    }
}
