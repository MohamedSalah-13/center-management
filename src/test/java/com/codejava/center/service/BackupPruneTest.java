package com.codejava.center.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * الحذف نفسه على قرص حقيقي.
 *
 * <p>{@link BackupRetentionTest} يغطّي القرار - أيّ الأسماء تُحذف - وهو حساب خالص. هذا الصنف
 * يغطّي ما بعده: أن الملف يختفي فعلاً من المجلد، وأن ما لم ينشئه البرنامج يبقى. الشكوى التي
 * وُلد منها ("النسخ القديمة لا تُحذف") تقع في هذه المسافة بالذات، ولا يراها اختبار لا يلمس
 * نظام الملفات.</p>
 *
 * <p>{@code auditService} يبقى {@code null}: {@link BackupService#prune} لا يمسّه، وتمرير
 * بديل وهمي هنا يوحي بأنه جزء من العملية وهو ليس كذلك.</p>
 */
class BackupPruneTest {

    private final BackupService backupService = new BackupService(null);

    @TempDir
    Path folder;

    @Test
    void deletesTheOldestUntilOnlyTheLimitRemains() throws IOException {
        for (int day = 1; day <= 35; day++) {
            write("backup_2026-08-%02d_02-00-00.sql".formatted(day));
        }

        BackupRetention.Pruned summary = backupService.prune(folder, 30);

        assertThat(remaining()).hasSize(30)
                .contains("backup_2026-08-06_02-00-00.sql", "backup_2026-08-35_02-00-00.sql")
                .doesNotContain("backup_2026-08-05_02-00-00.sql");
        assertThat(summary.deleted()).isEqualTo(5);
        assertThat(summary.failed()).isZero();
        assertThat(summary.details()).isEqualTo("pruned=5");
    }

    @Test
    void leavesTheFolderAloneWhenItHoldsFewerThanTheLimit() throws IOException {
        write("backup_2026-08-01_02-00-00.sql");
        write("backup_2026-08-02_02-00-00.sql");

        assertThat(backupService.prune(folder, 30).deleted()).isZero();
        assertThat(remaining()).hasSize(2);
    }

    /** مجلد النسخ كثيراً ما يكون مجلد مستندات فيه ملفات لصاحب السنتر */
    @Test
    void neverDeletesAFileTheProgramDidNotWrite() throws IOException {
        write("backup_2026-08-01_02-00-00.sql");
        write("backup_2026-08-02_02-00-00.sql");
        write("كشف الحساب.pdf");
        write("readme.txt");

        backupService.prune(folder, 1);

        assertThat(remaining()).containsExactlyInAnyOrder(
                "backup_2026-08-02_02-00-00.sql", "كشف الحساب.pdf", "readme.txt");
    }

    /** الصفر اختيار صريح، ولا يُقرأ على أنه "لم يُضبط" */
    @Test
    void keepsEverythingWhenTheLimitIsZero() throws IOException {
        write("backup_2026-08-01_02-00-00.sql");
        write("backup_2026-08-02_02-00-00.sql");

        assertThat(backupService.prune(folder, BackupRetention.KEEP_ALL)).isEqualTo(BackupRetention.Pruned.OFF);
        assertThat(remaining()).hasSize(2);
    }

    @Test
    void deletesEncryptedBackupsToo() throws IOException {
        write("backup_2026-08-01_02-00-00.sql.enc");
        write("backup_2026-08-02_02-00-00.sql.enc");
        write("backup_2026-08-03_02-00-00.sql.enc");

        backupService.prune(folder, 1);

        assertThat(remaining()).containsExactly("backup_2026-08-03_02-00-00.sql.enc");
    }

    /** مجلد فرعي اسمه يشبه اسم نسخة لا يُحذف: الحذف على الملفات وحدها */
    @Test
    void ignoresDirectories() throws IOException {
        Files.createDirectory(folder.resolve("backup_2026-08-01_02-00-00.sql"));
        write("backup_2026-08-02_02-00-00.sql");
        write("backup_2026-08-03_02-00-00.sql");

        backupService.prune(folder, 1);

        assertThat(remaining()).containsExactlyInAnyOrder(
                "backup_2026-08-01_02-00-00.sql", "backup_2026-08-03_02-00-00.sql");
    }

    /**
     * المجلد غير موجود - قرص خارجي فُصل بين النسخة والحذف. لا يرمي: النسخة تمّت،
     * والسطر في سجل المراقبة هو ما يقول إن الحذف لم يقع.
     */
    @Test
    void reportsInsteadOfThrowingWhenTheFolderCannotBeRead() {
        assertThat(backupService.prune(folder.resolve("gone"), 30).unreadable()).isTrue();
    }

    private void write(String name) throws IOException {
        Files.writeString(folder.resolve(name), "x");
    }

    private List<String> remaining() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
