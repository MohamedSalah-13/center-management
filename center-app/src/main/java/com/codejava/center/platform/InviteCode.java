package com.codejava.center.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * رمز الدعوة: ما يُسلَّم لصاحب السنتر مرةً واحدة، وبصمتُه التي تبقى في قاعدة المنصة.
 *
 * <p>عشرون بايتاً من {@link SecureRandom} - مئة وستون بتاً. هذا ليس رقماً مختاراً
 * للأناقة: الرمز يفتح حساب مدير سنترٍ كامل، ومن يستطيع تجريب الرموز يستطيع أخذ سنترٍ
 * لم يُهيَّأ بعد. بهذا الطول لا يُستنفد المدى بالتجريب مهما طال.</p>
 *
 * <p>ويُعرض بحروف كبيرة مقسّمة بشرطات لأنه يُنطق في هاتف ويُنسخ بخط اليد؛ ويُقرأ
 * بتجاهل الشرطات والحالة، فمن كتبه بحروف صغيرة لا يُقال له "رمز خاطئ" عن شيء صحيح.</p>
 *
 * <p><b>والبصمة SHA-256 لا BCrypt.</b> التفريق مقصود: BCrypt بطيء عمداً لأن كلمة
 * يختارها إنسان تُخمَّن بالقوائم، ورمزٌ عشوائي بمئة وستين بتاً لا يُخمَّن أصلاً - فلا
 * فائدة من البطء، وله كلفة: البحث عن رمز مقدَّم يحتاج فهرساً، وBCrypt لا يُفهرَس
 * لأن لكل بصمة ملحاً مختلفاً.</p>
 */
public final class InviteCode {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int BYTES = 20;

    /** طول المقطع بين شرطتين في العرض */
    private static final int GROUP = 5;

    private InviteCode() {
    }

    /** رمزٌ جديد، بالصيغة التي تُسلَّم لصاحب السنتر */
    public static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        String raw = HexFormat.of().withUpperCase().formatHex(bytes);

        StringBuilder grouped = new StringBuilder(raw.length() + raw.length() / GROUP);
        for (int index = 0; index < raw.length(); index++) {
            if (index > 0 && index % GROUP == 0) {
                grouped.append('-');
            }
            grouped.append(raw.charAt(index));
        }
        return grouped.toString();
    }

    /** بصمة الرمز كما تُحفظ ويُبحث بها - تتجاهل الشرطات وحالة الحروف */
    public static String fingerprint(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("invite code is required");
        }
        String normalised = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 مفروضة على كل تنفيذ لجافا؛ غيابها يعني بيئة مكسورة لا حالة تُعالَج
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
