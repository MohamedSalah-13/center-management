package com.codejava.center.util;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * تشفير ملف النسخة الاحتياطية.
 *
 * <p>ملف الـ dump نصّ صريح فيه كل ما في السنتر: أسماء الطلاب وأرقام أولياء أمورهم
 * وأرصدتهم وكل حركات الخزينة. والنسخة الاحتياطية — بحكم كونها نسخة احتياطية — هي
 * الملف الوحيد الذي يُنسخ عمداً خارج الجهاز: على فلاشة، على قرص خارجي، إلى Drive.
 * فقدان الفلاشة يجب ألا يعني فقدان بيانات كل طالب في السنتر.</p>
 *
 * <p><b>AES-256-GCM</b> ومفتاح مشتق بـ PBKDF2-HMAC-SHA256. الاختيار مقصود في نقطتين:
 * GCM يتحقق من سلامة الملف (وسم المصادقة) فلا تُستعاد نسخة تلف نصفها على الفلاشة
 * دون أن يُكتشف ذلك، و PBKDF2 بعدد لفّات كبير يجعل تخمين كلمة مرور بشرية بطيئاً.</p>
 *
 * <p>ترويسة الملف صريحة (رقم سحري + رقم إصدار) لسببين: تعرف الشاشة أن الملف مشفَّر
 * قبل أن تسأل عن كلمة المرور، ويمكن لإصدار لاحق تغيير الخوارزمية دون أن تصبح النسخ
 * القديمة غير قابلة للقراءة.</p>
 *
 * <p><b>ما لا يفعله:</b> الملف يُفكّ بكلمة المرور وحدها. من فقد كلمة المرور فقد النسخة —
 * لا يوجد باب خلفي ولا استعادة من قاعدة البيانات، ولهذا تُصرّ الشاشة على تنبيه المستخدم
 * بذلك عند تفعيل التشفير.</p>
 */
public final class BackupCrypto {

    /** بادئة تميّز ملفاتنا عن أي ملف آخر، وتُقرأ كنصّ في محرر سداسي عند تشخيص عطل */
    private static final byte[] MAGIC = "CENTERBK".getBytes(StandardCharsets.US_ASCII);

    private static final byte FORMAT_VERSION = 1;

    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";

    private static final int KEY_BITS = 256;
    private static final int TAG_BITS = 128;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12; // الطول الذي يوصي به معيار GCM
    private static final int ITERATIONS = 210_000;

    private static final int BUFFER_BYTES = 64 * 1024;

    /** لاحقة ملف النسخة المشفَّرة: {@code backup_2026-08-04_02-00-00.sql.enc} */
    public static final String ENCRYPTED_SUFFIX = ".enc";

    private BackupCrypto() {
    }

    /** يُرمى حين يُفكّ الملف بكلمة مرور غير التي شُفِّر بها، أو حين يكون الملف تالفاً */
    public static class WrongPassphraseException extends IllegalStateException {
        public WrongPassphraseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** هل يبدأ الملف بترويسة نسخة مشفَّرة (لا يعتمد على الامتداد وحده) */
    public static boolean isEncrypted(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(MAGIC.length);
            return Arrays.equals(header, MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * يشفّر ما يخرج من {@code plain} إلى {@code target}.
     *
     * <p>المدخل تيار لا ملف لأن مصدره مخرجات {@code mysqldump} مباشرةً: كتابة الـ dump
     * صريحاً على القرص ثم تشفيره تترك نسخة كاملة غير مشفَّرة على الجهاز، ولو انقطع
     * التيار بينهما بقيت هناك إلى الأبد.</p>
     */
    public static void encrypt(InputStream plain, Path target, char[] passphrase) throws IOException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);

        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(MAGIC);
            out.write(FORMAT_VERSION);
            out.write(salt);
            out.write(iv);

            transform(cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv), plain, out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(I18n.get("error.backup.encryptFailed"), e);
        }
    }

    /**
     * يفكّ {@code source} إلى {@code target} ويتحقق من وسم المصادقة قبل أن يعود.
     *
     * <p>يكتب ملفاً كاملاً ولا يعيد تياراً عن قصد: وسم GCM لا يُتحقَّق منه إلا عند آخر
     * بايت، فتمرير التيار مباشرةً إلى {@code mysql} يعني أن كلمة مرور خاطئة تُغرق قاعدة
     * البيانات ببيانات معطوبة ثم يُكتشف الخطأ. الملف الوسيط يجعل الفشل قبل أن تُلمس
     * القاعدة، وهو ما يحذفه المستدعي في {@code finally}.</p>
     */
    public static void decrypt(Path source, Path target, char[] passphrase) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {

            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                throw new IllegalStateException(I18n.get("error.backup.notEncrypted"));
            }
            int version = in.read();
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException(I18n.format("error.backup.unknownFormat", version));
            }

            byte[] salt = in.readNBytes(SALT_BYTES);
            byte[] iv = in.readNBytes(IV_BYTES);
            if (salt.length < SALT_BYTES || iv.length < IV_BYTES) {
                throw new IllegalStateException(I18n.get("error.backup.truncated"));
            }

            transform(cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv), in, out);
        } catch (AEADBadTagException e) {
            // كلمة مرور خاطئة وملف تالف يظهران بالخطأ نفسه: GCM لا يميّز بينهما
            throw new WrongPassphraseException(I18n.get("error.backup.wrongPassphrase"), e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(I18n.get("error.backup.decryptFailed"), e);
        }
    }

    /**
     * يمرّر التيار على الشفرة على دفعات.
     * {@code doFinal} في النهاية هو ما يكتب وسم المصادقة عند التشفير ويتحقق منه عند
     * الفكّ، فأي مسار لا يمرّ به يُنتج ملفاً لا يمكن فكّه.
     */
    private static void transform(Cipher cipher, InputStream in, OutputStream out)
            throws IOException, GeneralSecurityException {
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = in.read(buffer)) != -1) {
            writeIfAny(out, cipher.update(buffer, 0, read));
        }
        writeIfAny(out, cipher.doFinal());
    }

    /** {@code update} يعيد {@code null} حين لا تكتمل كتلة بعد */
    private static void writeIfAny(OutputStream out, byte[] block) throws IOException {
        if (block != null && block.length > 0) {
            out.write(block);
        }
    }

    private static Cipher cipher(int mode, char[] passphrase, byte[] salt, byte[] iv)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            Arrays.fill(key, (byte) 0);
            return cipher;
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
