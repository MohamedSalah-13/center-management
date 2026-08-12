package com.codejava.center.util;

import java.nio.file.Path;

/** مكان سجل التشغيل الذي يستطيع برنامج jpackage الكتابة فيه ويستطيع الدعم العثور عليه. */
public final class ApplicationLogs {

    public static final String LOG_FILE_PROPERTY = "center.log.file";
    public static final String LOG_DIRECTORY_ENV = "CENTER_LOG_DIR";
    private static final String DEFAULT_DIRECTORY = ".center-management/logs";
    private static final String FILE_NAME = "center-management.log";

    private ApplicationLogs() {
    }

    /**
     * يثبت المسار قبل إقلاع Spring؛ عندها يقرأه نظام Logging منذ أول سطر إقلاع.
     * الخاصية الصريحة لها الأولوية لتبقى قابلة للضبط في الاختبارات وأدوات الدعم.
     */
    public static void configure() {
        System.setProperty(LOG_FILE_PROPERTY, currentFile().toString());
    }

    public static Path currentFile() {
        String explicitFile = System.getProperty(LOG_FILE_PROPERTY);
        if (explicitFile != null && !explicitFile.isBlank()) {
            return absolute(explicitFile);
        }

        String explicitDirectory = System.getenv(LOG_DIRECTORY_ENV);
        if (explicitDirectory != null && !explicitDirectory.isBlank()) {
            return absolute(explicitDirectory).resolve(FILE_NAME);
        }

        String userHome = System.getProperty("user.home", System.getProperty("user.dir", "."));
        return absolute(userHome).resolve(DEFAULT_DIRECTORY).resolve(FILE_NAME);
    }

    public static Path directory() {
        return currentFile().getParent();
    }

    private static Path absolute(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }
}
