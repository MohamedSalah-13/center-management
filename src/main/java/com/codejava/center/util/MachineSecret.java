package com.codejava.center.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * تعمية سرّ محفوظ على هذا الجهاز ({@link java.util.prefs.Preferences}).
 *
 * <p>يخدم سرَّين لا واحداً — كلمة مرور النسخ الاحتياطية ومفتاح مزوّد الرسائل — ولهذا
 * خرج من {@link BackupPreferences}: نسخ خوارزمية تشفير في موضعين يعني أن إصلاح خلل
 * في أحدهما يترك الآخر كما هو.</p>
 *
 * <p><b>حدود ما يقدّمه:</b> النسخة الليلية تعمل بلا مستخدم أمام الشاشة، والمزوّد يُرسل
 * له في أي وقت، فلا مفرّ من وجود السرّ على الجهاز. التعمية تمنع قراءته بفتح محرر
 * السجل، لكنها لا تحمي من مهاجم يعمل بصلاحية المستخدم نفسه: المفتاح مشتقّ من ثوابت
 * الجهاز والحساب لا من سرّ يعرفه المستخدم وحده.</p>
 *
 * <p>الغرض ({@code purpose}) داخل مادة المفتاح، فسرّ النسخ لا يُفكّ بمفتاح سرّ الرسائل
 * ولو نُقلت القيمة بين الاثنين. <b>تغيير نصّ الغرض يُبطل كل ما حُفظ به.</b></p>
 */
public final class MachineSecret {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final String purpose;

    private MachineSecret(String purpose) {
        this.purpose = purpose;
    }

    public static MachineSecret forPurpose(String purpose) {
        return new MachineSecret(purpose);
    }

    /** يعمّي القيمة نصاً صالحاً للحفظ في تفضيلات الجهاز */
    public String conceal(char[] value) {
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
            throw new IllegalStateException(I18n.get("error.preferences.secretFailed"), e);
        }
    }

    /**
     * @return القيمة الأصلية، أو {@code null} إن كُتبت بمفتاح جهاز آخر (سجل منسوخ) أو تلفت.
     * الفشل هنا ليس خطأ يُعرض بل سرّ غير مضبوط على هذا الجهاز، والشاشة تطلبه من جديد.
     */
    public char[] reveal(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, machineKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).toCharArray();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKeySpec machineKey() throws Exception {
        String material = purpose + '|' + System.getProperty("user.name", "")
                + '|' + System.getProperty("os.name", "");
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
