package com.codejava.center.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * تشفير ملف النسخة الاحتياطية.
 *
 * <p>الخطأ في هذا الصنف من نوع لا يُكتشف إلا عند الحاجة: نسخة تُكتب كل ليلة ثم لا تُفكّ
 * يوم يُستعاد منها. الاختبارات تدور دورة كاملة (تشفير ← فكّ) وتتأكد من أن كلمة مرور
 * خاطئة أو ملفاً معدَّلاً يفشلان صراحةً بدل أن يُنتجا بيانات معطوبة تُصبّ في القاعدة.</p>
 */
class BackupCryptoTest {

    private static final char[] PASSPHRASE = "correct-horse-battery".toCharArray();

    /** نصّ فيه عربية: الترميز جزء مما يجب أن يعود سليماً */
    private static final String DUMP = """
            -- MySQL dump
            INSERT INTO students (name) VALUES ('محمد صلاح');
            """;

    @Test
    void encryptedContentComesBackByteForByte(@TempDir Path directory) throws IOException {
        Path encrypted = directory.resolve("backup.sql.enc");
        Path decrypted = directory.resolve("restored.sql");

        BackupCrypto.encrypt(source(DUMP), encrypted, PASSPHRASE.clone());
        BackupCrypto.decrypt(encrypted, decrypted, PASSPHRASE.clone());

        assertThat(Files.readString(decrypted, StandardCharsets.UTF_8)).isEqualTo(DUMP);
    }

    /** ما يُكتب على الفلاشة يجب ألا يكون مقروءاً: هذا هو سبب وجود التشفير كله */
    @Test
    void theEncryptedFileDoesNotContainThePlainText(@TempDir Path directory) throws IOException {
        Path encrypted = directory.resolve("backup.sql.enc");

        BackupCrypto.encrypt(source(DUMP), encrypted, PASSPHRASE.clone());

        assertThat(Files.readString(encrypted, StandardCharsets.ISO_8859_1))
                .doesNotContain("INSERT INTO students");
    }

    @Test
    void aWrongPassphraseIsRejected(@TempDir Path directory) throws IOException {
        Path encrypted = directory.resolve("backup.sql.enc");
        BackupCrypto.encrypt(source(DUMP), encrypted, PASSPHRASE.clone());

        assertThatThrownBy(() -> BackupCrypto.decrypt(
                encrypted, directory.resolve("restored.sql"), "wrong-password".toCharArray()))
                .isInstanceOf(BackupCrypto.WrongPassphraseException.class)
                .hasMessage(I18n.get("error.backup.wrongPassphrase"));
    }

    /**
     * وسم المصادقة في GCM هو ما يجعل نسخة تلفت على فلاشة تفشل صراحةً بدل أن تُستعاد
     * ناقصة فوق قاعدة بيانات سليمة.
     */
    @Test
    void aTamperedFileIsRejected(@TempDir Path directory) throws IOException {
        Path encrypted = directory.resolve("backup.sql.enc");
        BackupCrypto.encrypt(source(DUMP), encrypted, PASSPHRASE.clone());

        byte[] bytes = Files.readAllBytes(encrypted);
        bytes[bytes.length - 20] ^= 0x01; // بايت واحد في وسط البيانات المشفَّرة
        Files.write(encrypted, bytes);

        assertThatThrownBy(() -> BackupCrypto.decrypt(
                encrypted, directory.resolve("restored.sql"), PASSPHRASE.clone()))
                .isInstanceOf(BackupCrypto.WrongPassphraseException.class);
    }

    @Test
    void anEncryptedFileIsRecognisedByItsHeaderNotItsExtension(@TempDir Path directory) throws IOException {
        Path misnamed = directory.resolve("backup.sql");
        BackupCrypto.encrypt(source(DUMP), misnamed, PASSPHRASE.clone());

        assertThat(BackupCrypto.isEncrypted(misnamed)).isTrue();
    }

    @Test
    void aPlainDumpIsNotMistakenForAnEncryptedOne(@TempDir Path directory) throws IOException {
        Path plain = directory.resolve("backup.sql");
        Files.writeString(plain, DUMP);

        assertThat(BackupCrypto.isEncrypted(plain)).isFalse();
    }

    @Test
    void decryptingAPlainFileFailsWithAClearMessage(@TempDir Path directory) throws IOException {
        Path plain = directory.resolve("backup.sql");
        Files.writeString(plain, DUMP);

        assertThatThrownBy(() -> BackupCrypto.decrypt(
                plain, directory.resolve("restored.sql"), PASSPHRASE.clone()))
                .hasMessage(I18n.get("error.backup.notEncrypted"));
    }

    /** الملحّ (salt) عشوائي لكل نسخة، فنسختان لنفس البيانات لا تتطابقان */
    @Test
    void twoBackupsOfTheSameDataDifferOnDisk(@TempDir Path directory) throws IOException {
        Path first = directory.resolve("first.sql.enc");
        Path second = directory.resolve("second.sql.enc");

        BackupCrypto.encrypt(source(DUMP), first, PASSPHRASE.clone());
        BackupCrypto.encrypt(source(DUMP), second, PASSPHRASE.clone());

        assertThat(Files.readAllBytes(first)).isNotEqualTo(Files.readAllBytes(second));
    }

    private ByteArrayInputStream source(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
