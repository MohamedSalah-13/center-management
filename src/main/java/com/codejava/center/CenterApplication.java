package com.codejava.center;

import com.codejava.center.domain.User;
import com.codejava.center.repository.UserRepository;
import javafx.application.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
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
            // تشفير كلمة المرور "123"
            String hashedPassword = passwordEncoder.encode("admin");

            // حفظ المستخدم بالكلمة المشفرة وتحديد الصلاحية ADMIN
            userRepository.save(new User(null, "admin", hashedPassword, "ADMIN"));
        }
    }
}