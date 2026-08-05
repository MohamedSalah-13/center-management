package com.codejava.center.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * كلمة مرور تشفير النسخ الاحتياطية، محفوظة <b>لكل جهاز</b> عبر {@link Preferences}.
 *
 * <p><b>لماذا ليست في {@code CenterSettings} مثل بقية إعدادات النسخ؟</b> لأن كلمة المرور
 * ستُحفظ حينئذٍ داخل قاعدة البيانات التي تحميها، فيخرج مفتاح النسخة داخل النسخة نفسها
 * ويصبح التشفير بلا معنى. والأسوأ أن الحالة التي وُجدت النسخ من أجلها — ضياع قاعدة
 * البيانات — هي بالضبط الحالة التي تضيع فيها كلمة المرور فتصبح كل النسخ غير قابلة للفكّ.</p>
 *
 * <p>وككل ما يُحفظ لكل جهاز في هذا المشروع (اللغة، الطابعة) فلا ملف ترحيل Flyway له.</p>
 *
 * <p><b>حدود التعمية أدناه:</b> الحفظ التلقائي الليلي يعمل بلا مستخدم أمام الشاشة، فلا
 * مفرّ من تخزين كلمة المرور على الجهاز. تخزينها معمّاة يمنع قراءتها بفتح محرر السجل،
 * لكنه لا يحمي من مهاجم يملك صلاحية المستخدم نفسه على الجهاز — المفتاح مشتقّ من ثوابت
 * الجهاز والحساب لا من سرّ يعرفه المستخدم وحده. الخطر الذي يعالجه التشفير هو خروج ملف
 * النسخة من الجهاز (فلاشة، قرص خارجي، Drive)، وذاك يعالجه كاملاً.</p>
 */
public final class BackupPreferences {

    private static final String ENABLED_KEY = "backup.encrypt";
    private static final String PASSPHRASE_KEY = "backup.passphrase";

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    /** ثابت يخصّ البرنامج، يُخلط مع بيانات الحساب حتى لا يكون المفتاح واحداً عند الجميع */
    private static final String OBFUSCATION_SEED = "center-management/backup-passphrase/v1";

    private BackupPreferences() {
    }

    /** هل تُشفَّر النسخ التي يأخذها هذا الجهاز */
    public static boolean encryptionEnabled() {
        try {
            return prefs().getBoolean(ENABLED_KEY, false);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * كلمة مرور التشفير، أو {@code null} إن لم تُضبط.
     *
     * <p>تعود {@code char[]} لا {@code String}: النص الثابت يبقى في ذاكرة الـ JVM حتى
     * يجمعه الكانس، والمصفوفة يمكن محوها فور استعمالها.</p>
     */
    public static char[] passphrase() {
        String stored = read(PASSPHRASE_KEY);
        if (stored == null) {
            return null;
        }
        try {
            return reveal(stored);
        } catch (Exception e) {
            // القيمة المحفوظة كُتبت بمفتاح جهاز آخر (سجل منسوخ) أو تلفت
            return null;
        }
    }

    /** هل ضُبطت كلمة مرور على هذا الجهاز — للعرض في الشاشة دون كشف الكلمة نفسها */
    public static boolean hasPassphrase() {
        return read(PASSPHRASE_KEY) != null;
    }

    /**
     * يضبط التشفير وكلمة مروره معاً.
     *
     * <p>الدالة واحدة للاثنين عن قصد: تفعيل التشفير بلا كلمة مرور يعني نسخاً ليلية تفشل
     * كل ليلة، وحفظ كلمة مرور مع تعطيل التشفير يترك سرّاً على الجهاز بلا فائدة.</p>
     *
     * @param passphrase تُمحى محتوياتها بعد الحفظ
     */
    public static void set(boolean enabled, char[] passphrase) {
        boolean usable = enabled && passphrase != null && passphrase.length > 0;
        try {
            Preferences prefs = prefs();
            prefs.putBoolean(ENABLED_KEY, usable);
            if (usable) {
                prefs.put(PASSPHRASE_KEY, conceal(passphrase));
            } else {
                prefs.remove(PASSPHRASE_KEY);
            }
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.backup.passphraseNotSaved"), e);
        } finally {
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }
    }

    // ------------------------------------------------------------- التعمية

    private static String conceal(char[] value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, machineKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(new String(value).getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException(I18n.get("error.backup.passphraseNotSaved"), e);
        }
    }

    private static char[] reveal(String stored) throws Exception {
        byte[] combined = Base64.getDecoder().decode(stored);
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] encrypted = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, machineKey(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).toCharArray();
    }

    private static SecretKeySpec machineKey() throws Exception {
        String material = OBFUSCATION_SEED + '|' + System.getProperty("user.name", "")
                + '|' + System.getProperty("os.name", "");
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    // ------------------------------------------------------------- التخزين

    private static String read(String key) {
        try {
            String saved = prefs().get(key, null);
            return saved == null || saved.isBlank() ? null : saved;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(BackupPreferences.class);
    }
}
