package com.codejava.center;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import javafx.application.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EnableScheduling // بدونها لا تعمل مهمة النسخ الاحتياطي المجدولة في BackupService إطلاقاً
public class CenterApplication implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // سيتم جلبها الآن من كلاس SecurityConfig بأمان

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // التحقق من عدم وجود المستخدم لتجنب تكرار إنشائه
        if (userRepository.findByUsername("admin").isEmpty()) {
            String initialPassword = resolveInitialAdminPassword();

            userRepository.save(new User(null, "admin", passwordEncoder.encode(initialPassword), Role.ADMIN));
        }
    }

    /**
     * كلمة مرور المدير الأولى.
     * لا تُستخدم قيمة ثابتة مثل "admin": نظام يتعامل مع نقود ولا يجوز أن يُنشر
     * بكلمة مرور معروفة لكل من قرأ الكود.
     * تُقرأ من متغير البيئة ADMIN_INITIAL_PASSWORD، وإن لم يُضبط تُولَّد عشوائياً
     * وتُطبع مرة واحدة عند الإنشاء فقط.
     */
    private String resolveInitialAdminPassword() {
        String fromEnv = System.getenv("ADMIN_INITIAL_PASSWORD");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        byte[] randomBytes = new byte[12];
        new SecureRandom().nextBytes(randomBytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        System.out.println("""

                ==========================================================
                  تم إنشاء حساب المدير الافتراضي لأول مرة
                  اسم المستخدم : admin
                  كلمة المرور  : %s
                  سجّل هذه الكلمة الآن ثم غيّرها من شاشة إدارة المستخدمين.
                  لن تُعرض مرة أخرى.
                ==========================================================
                """.formatted(generated));

        return generated;
    }
}