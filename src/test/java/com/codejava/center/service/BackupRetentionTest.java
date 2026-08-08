package com.codejava.center.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حذف النسخ القديمة بعد كل نسخة ناجحة.
 *
 * <p>الخطأ هنا صامت في الاتجاهين وكلاهما يُكتشف متأخراً: إمّا لا يُحذف شيء فيمتلئ القرص
 * ذات ليلة فتتوقف النسخ كلها، وإمّا يُحذف الخطأ منها فتُطلب نسخة الأسبوع الماضي ولا
 * تكون موجودة. لا شاشة تُظهر أياً من الحالتين قبل وقوع الضرر.</p>
 *
 * <p>لا سياق Spring ولا قرص: {@link BackupRetention} قرار خالص، لنفس سبب كون
 * {@link BackupSchedule} كذلك.</p>
 */
class BackupRetentionTest {

    @Test
    void keepsTheNewestAndNamesTheRestForDeletion() {
        List<String> files = List.of(
                "backup_2026-08-01_02-00-00.sql",
                "backup_2026-08-02_02-00-00.sql",
                "backup_2026-08-03_02-00-00.sql",
                "backup_2026-08-04_02-00-00.sql");

        assertThat(BackupRetention.obsolete(files, 2)).containsExactly(
                "backup_2026-08-02_02-00-00.sql",
                "backup_2026-08-01_02-00-00.sql");
    }

    @Test
    void deletesNothingWhenTheFolderHoldsFewerThanTheLimit() {
        List<String> files = List.of("backup_2026-08-01_02-00-00.sql", "backup_2026-08-02_02-00-00.sql");

        assertThat(BackupRetention.obsolete(files, 30)).isEmpty();
    }

    /** الصفر اختيار صريح بالاحتفاظ بالكل، لا قيمة غائبة */
    @Test
    void zeroKeepsEveryBackup() {
        List<String> files = List.of(
                "backup_2026-08-01_02-00-00.sql",
                "backup_2026-08-02_02-00-00.sql",
                "backup_2026-08-03_02-00-00.sql");

        assertThat(BackupRetention.obsolete(files, BackupRetention.KEEP_ALL)).isEmpty();
    }

    /** مجلد النسخ قد يكون مجلد مستندات فيه ملفات لصاحب السنتر */
    @Test
    void neverTouchesAFileTheProgramDidNotWrite() {
        List<String> files = List.of(
                "backup_2026-08-01_02-00-00.sql",
                "backup_2026-08-02_02-00-00.sql",
                "قائمة الطلاب.xlsx",
                "backup_notes.txt",
                "old_backup_2026-01-01_02-00-00.sql");

        assertThat(BackupRetention.obsolete(files, 1))
                .containsExactly("backup_2026-08-01_02-00-00.sql");
    }

    /**
     * نسخة من ملف نسخة ليست نسخة.
     *
     * <p>مستكشف ويندوز يسمّي المنسوخ {@code backup_2026-08-04_18-42-14.sql - Copy.enc}:
     * ينتهي بـ {@code .enc} ولا ينتهي بـ {@code .sql.enc}، فلا يعدّه البرنامج ولا يحذفه.
     * وهذا هو المقصود لا نقص فيه - الاسم يقول إن يداً بشرية صنعته، وقاعدة "لا يُحذف إلا ما
     * كتبه البرنامج" هي ما تسمح بأن يكون مجلد النسخ مجلد مستندات عادياً.</p>
     *
     * <p>الأثر العملي الذي يُربك: مجلد فيه سبعة وثمانون ملفاً قد يكون فيه خمس عشرة نسخة
     * فقط، فيبدو الحدّ ثلاثون متجاوَزاً وهو ليس كذلك. سطر {@code pruned=0} في سجل المراقبة
     * هو ما يفصل هذه الحالة عن عطل حقيقي.</p>
     */
    @Test
    void doesNotCountOrDeleteFileManagerCopies() {
        List<String> files = List.of(
                "backup_2026-08-04_18-42-14.sql.enc",
                "backup_2026-08-04_18-42-14.sql - Copy.enc",
                "backup_2026-08-04_18-42-14.sql - Copy (2).enc",
                "backup_2026-08-05_06-30-17.sql.enc",
                "backup_2026-08-05_06-30-17.sql - Copy.enc");

        assertThat(BackupRetention.obsolete(files, 1))
                .containsExactly("backup_2026-08-04_18-42-14.sql.enc");
    }

    @Test
    void countsEncryptedBackupsAlongsidePlainOnes() {
        List<String> files = List.of(
                "backup_2026-08-01_02-00-00.sql",
                "backup_2026-08-02_02-00-00.sql.enc",
                "backup_2026-08-03_02-00-00.sql.enc");

        assertThat(BackupRetention.obsolete(files, 2))
                .containsExactly("backup_2026-08-01_02-00-00.sql");
    }

    /** النسخ القديمة كانت تحمل التاريخ وحده؛ مجلد قديم لا يزال يحوي ملفات بذلك الاسم */
    @Test
    void recognisesTheOlderDateOnlyFileName() {
        assertThat(BackupRetention.isBackupFile("backup_2026-08-01.sql")).isTrue();
    }

    /**
     * الحالة التي كانت تصمت: عمود {@code backup_retention_count} يقبل NULL و{@code V2}
     * أضافه فارغاً لكل قاعدة قائمة، فكانت الشاشة تعرض 30 ولا يُحذف شيء أبداً.
     */
    @Test
    void missingSettingMeansTheDefaultTheScreenShowsNotKeepEverything() {
        assertThat(BackupRetention.resolve(null)).isEqualTo(BackupRetention.DEFAULT_COUNT);
        assertThat(BackupRetention.obsolete(
                List.of("backup_2026-08-01_02-00-00.sql", "backup_2026-08-02_02-00-00.sql"),
                BackupRetention.resolve(null))).isEmpty();
    }

    @Test
    void keepsAnExplicitZeroAsKeepEverything() {
        assertThat(BackupRetention.resolve(0)).isEqualTo(BackupRetention.KEEP_ALL);
    }

    @Test
    void clampsAValueOutsideTheRange() {
        assertThat(BackupRetention.resolve(-5)).isEqualTo(BackupRetention.KEEP_ALL);
        assertThat(BackupRetention.resolve(100_000)).isEqualTo(BackupRetention.MAX_COUNT);
    }
}
