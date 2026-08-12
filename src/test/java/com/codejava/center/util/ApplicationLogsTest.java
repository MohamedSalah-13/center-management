package com.codejava.center.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLogsTest {

    @TempDir Path tempDirectory;

    @Test
    void explicitLogFileIsNormalizedAndPublishedForSpring() {
        String previous = System.getProperty(ApplicationLogs.LOG_FILE_PROPERTY);
        Path requested = tempDirectory.resolve("support").resolve("client.log");
        try {
            System.setProperty(ApplicationLogs.LOG_FILE_PROPERTY, requested.toString());

            ApplicationLogs.configure();

            assertThat(ApplicationLogs.currentFile()).isEqualTo(requested.toAbsolutePath().normalize());
            assertThat(System.getProperty(ApplicationLogs.LOG_FILE_PROPERTY))
                    .isEqualTo(requested.toAbsolutePath().normalize().toString());
        } finally {
            restore(previous);
        }
    }

    @Test
    void logDirectoryIsAlwaysTheParentOfThePublishedFile() {
        String previous = System.getProperty(ApplicationLogs.LOG_FILE_PROPERTY);
        try {
            System.clearProperty(ApplicationLogs.LOG_FILE_PROPERTY);

            assertThat(ApplicationLogs.currentFile()).isAbsolute();
            assertThat(ApplicationLogs.directory()).isEqualTo(ApplicationLogs.currentFile().getParent());
            assertThat(ApplicationLogs.currentFile().getFileName().toString())
                    .isEqualTo("center-management.log");
        } finally {
            restore(previous);
        }
    }

    private void restore(String previous) {
        if (previous == null) {
            System.clearProperty(ApplicationLogs.LOG_FILE_PROPERTY);
        } else {
            System.setProperty(ApplicationLogs.LOG_FILE_PROPERTY, previous);
        }
    }
}
