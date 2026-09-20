package com.codejava.center.config;

import com.codejava.center.core.security.LoginThrottle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    // Spring سيقوم بإنشاء هذه الأداة هنا لكي تكون متاحة لأي مكان في المشروع
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * عدّاد محاولات الدخول الفاشلة.
     *
     * <p>bean واحد للبرنامج كله لا حقل داخل {@code AuthService}: هو <b>حالة</b> يجب أن
     * تبقى بين الاستدعاءات، وحقلٌ في خدمة يصعب تصفيره في اختبار. ونقيٌّ في النواة
     * فيُختبر القرارُ نفسه - متى يُقفل ومتى يُفتح - بساعة مثبَّتة وبلا سياق Spring.</p>
     */
    @Bean
    public LoginThrottle loginThrottle() {
        return new LoginThrottle();
    }
}
