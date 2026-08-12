package com.codejava.center;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
// بدونها لا يُنشئ Spring مشغّل المهام الذي يحقنه BackupScheduler، فلا تعمل أي نسخة تلقائية
@EnableScheduling
public class CenterApplication {

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }

}
