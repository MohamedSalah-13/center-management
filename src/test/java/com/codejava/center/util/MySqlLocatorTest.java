package com.codejava.center.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * البحث التلقائي عن مجلد أدوات MySQL على القرص.
 *
 * <p>الفشل الصامت هنا يعني نسخاً احتياطية تفشل عند عملاء لم يضبطوا
 * {@code CENTER_BACKUP_MYSQL_BIN_DIR} يدوياً، رغم أن MySQL مثبَّت فعلاً في مسار معروف —
 * وهذا بالضبط ما جعل first-install.md يشرح المسار يدوياً من الأساس.</p>
 */
class MySqlLocatorTest {

    @Test
    void findsToolsDirectlyUnderBin(@TempDir Path root) throws IOException {
        Path bin = root.resolve("bin");
        Files.createDirectories(bin);
        Files.createFile(bin.resolve("mysqldump.exe"));

        assertThat(MySqlLocator.searchUnder(root)).isEqualTo(bin.toString());
    }

    @Test
    void findsToolsUnderVersionedSubfolder(@TempDir Path root) throws IOException {
        Path bin = root.resolve("MySQL Server 8.0").resolve("bin");
        Files.createDirectories(bin);
        Files.createFile(bin.resolve("mysqldump.exe"));

        assertThat(MySqlLocator.searchUnder(root)).isEqualTo(bin.toString());
    }

    @Test
    void prefersTheHighestSortedVersionWhenSeveralAreInstalled(@TempDir Path root) throws IOException {
        Path oldBin = root.resolve("MySQL Server 5.7").resolve("bin");
        Path newBin = root.resolve("MySQL Server 8.0").resolve("bin");
        Files.createDirectories(oldBin);
        Files.createDirectories(newBin);
        Files.createFile(oldBin.resolve("mysqldump.exe"));
        Files.createFile(newBin.resolve("mysqldump.exe"));

        assertThat(MySqlLocator.searchUnder(root)).isEqualTo(newBin.toString());
    }

    @Test
    void returnsNullWhenNothingMatches(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("unrelated"));

        assertThat(MySqlLocator.searchUnder(root)).isNull();
    }

    @Test
    void returnsNullForAMissingRoot(@TempDir Path root) {
        assertThat(MySqlLocator.searchUnder(root.resolve("does-not-exist"))).isNull();
    }
}
