package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final CenterSettingsRepository centerSettingsRepository;

    // بيانات الاتصال تُقرأ من الإعدادات (متغيرات البيئة) بدلاً من كتابتها داخل الكود
    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    // تشغيل تلقائي كل يوم الساعة 2 صباحاً
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledBackup() {
        CenterSettings settings = centerSettingsRepository.findById(1L).orElse(null);
        if (settings != null && settings.isAutoBackupEnabled() && settings.getBackupPath() != null) {
            executeBackup(settings.getBackupPath());
        }
    }

    // دالة أخذ النسخة الاحتياطية
    public boolean executeBackup(String targetDirectory) {
        try {
            String fileName = "backup_" + LocalDate.now() + ".sql";
            String fullPath = Path.of(targetDirectory, fileName).toString();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "-u", dbUsername,
                    extractDatabaseName(dbUrl),
                    "-r", fullPath
            );

            return runWithPassword(processBuilder);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // دالة استعادة النسخة الاحتياطية الجديدة
    @RequiresRole(Role.ADMIN)
    public boolean restoreBackup(String sqlFilePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysql",
                    "-u", dbUsername,
                    extractDatabaseName(dbUrl)
            );

            // قراءة ملف الـ SQL وتمريره كمدخل لأمر mysql
            processBuilder.redirectInput(new File(sqlFilePath));

            return runWithPassword(processBuilder);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * تنفيذ الأمر مع تمرير كلمة المرور عبر متغير البيئة MYSQL_PWD بدلاً من سطر الأوامر،
     * حتى لا تظهر كلمة المرور في قائمة العمليات (Task Manager / ps) لأي مستخدم على الجهاز.
     */
    private boolean runWithPassword(ProcessBuilder processBuilder) throws Exception {
        if (dbPassword != null && !dbPassword.isEmpty()) {
            processBuilder.environment().put("MYSQL_PWD", dbPassword);
        }
        // توجيه رسائل الخطأ لمخرجات البرنامج بدل ابتلاعها، لتشخيص سبب الفشل
        processBuilder.redirectErrorStream(false);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = processBuilder.start();
        return process.waitFor() == 0;
    }

    /**
     * استخراج اسم قاعدة البيانات من رابط JDBC
     * مثال: jdbc:mysql://localhost:3306/center_db?useSSL=false  ->  center_db
     */
    private String extractDatabaseName(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int lastSlash = withoutParams.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == withoutParams.length() - 1) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        return withoutParams.substring(lastSlash + 1);
    }
}
