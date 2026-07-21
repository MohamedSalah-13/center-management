package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.repository.CenterSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final CenterSettingsRepository centerSettingsRepository;

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
            String fullPath = targetDirectory + "/" + fileName;

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "-u", "root",
                    "-pm13ido",
                    "center_db",
                    "-r", fullPath
            );

            Process process = processBuilder.start();
            int processComplete = process.waitFor();

            return processComplete == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // دالة استعادة النسخة الاحتياطية الجديدة
    public boolean restoreBackup(String sqlFilePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysql",
                    "-u", "root",
                    "-pm13ido",
                    "center_db"
            );

            // قراءة ملف الـ SQL وتمريره كمدخل لأمر mysql
            processBuilder.redirectInput(new File(sqlFilePath));

            Process process = processBuilder.start();
            int processComplete = process.waitFor();

            return processComplete == 0; // 0 تعني نجاح العملية
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
