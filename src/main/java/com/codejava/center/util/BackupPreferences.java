package com.codejava.center.util;

import java.util.Arrays;
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
 * <p><b>حدود التعمية:</b> الحفظ التلقائي الليلي يعمل بلا مستخدم أمام الشاشة، فلا مفرّ
 * من تخزين كلمة المرور على الجهاز. التعمية ({@link MachineSecret}) تمنع قراءتها بفتح
 * محرر السجل، لكنها لا تحمي من مهاجم يملك صلاحية المستخدم نفسه على الجهاز. الخطر الذي
 * يعالجه التشفير هو خروج ملف النسخة من الجهاز (فلاشة، قرص خارجي، Drive)، وذاك يعالجه
 * كاملاً.</p>
 */
public final class BackupPreferences {

    private static final String ENABLED_KEY = "backup.encrypt";
    private static final String PASSPHRASE_KEY = "backup.passphrase";

    /**
     * ثابت يخصّ البرنامج، يُخلط مع بيانات الحساب حتى لا يكون المفتاح واحداً عند الجميع.
     * <b>تغييره يُبطل كل كلمة مرور محفوظة على أجهزة العملاء</b>، فتفشل النسخ الليلية عندهم.
     */
    private static final String OBFUSCATION_SEED = "center-management/backup-passphrase/v1";

    private static final MachineSecret SECRET = MachineSecret.forPurpose(OBFUSCATION_SEED);

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
        // القيمة المحفوظة قد تكون كُتبت بمفتاح جهاز آخر (سجل منسوخ) أو تلفت، فتعود null
        return stored == null ? null : SECRET.reveal(stored);
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
                prefs.put(PASSPHRASE_KEY, SECRET.conceal(passphrase));
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
