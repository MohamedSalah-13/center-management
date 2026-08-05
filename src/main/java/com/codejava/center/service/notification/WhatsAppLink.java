package com.codejava.center.service.notification;

import com.codejava.center.util.I18n;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * بناء رابط محادثة واتساب من قالب.
 *
 * <p>صنف خالص بلا واجهة ولا شبكة عن قصد: بناء الرابط هو الموضع الذي يصل فيه خطأ صامت
 * إلى ولي أمر — رسالة تصل مبتورة عند أول مسافة، أو رقم يذهب لشخص آخر — فيجب أن يُختبر
 * بلا تشغيل JavaFX ولا فتح متصفح. راجع {@code WhatsAppLinkTest}.</p>
 */
public final class WhatsAppLink {

    /** موضع الرقم بالصيغة الدولية في القالب */
    public static final String PHONE = "{phone}";

    /** موضع نص الرسالة في القالب */
    public static final String TEXT = "{text}";

    private WhatsAppLink() {
    }

    /**
     * @param style          نمط الرابط
     * @param customTemplate قالب المستخدم، يُستعمل مع {@link WhatsAppLinkStyle#CUSTOM} وحده
     * @throws IllegalArgumentException إن كان القالب لا يحمل موضع الرقم أو موضع النص،
     *                                  برسالة مترجَمة تُعرض في شاشة الإعدادات
     */
    public static String build(WhatsAppLinkStyle style, String customTemplate,
                               String internationalPhone, String message) {
        String template = templateOf(style, customTemplate);

        // قالب بلا {phone} يفتح محادثة فارغة، وقالب بلا {text} يفتح محادثة بلا رسالة
        // ويظنّ الموظف أنه أرسل. كلاهما يُكتشف هنا لا عند ولي الأمر.
        if (!template.contains(PHONE)) {
            throw new IllegalArgumentException(I18n.format("error.whatsapp.templateMissing", PHONE));
        }
        if (!template.contains(TEXT)) {
            throw new IllegalArgumentException(I18n.format("error.whatsapp.templateMissing", TEXT));
        }

        return template
                .replace(PHONE, internationalPhone == null ? "" : internationalPhone)
                .replace(TEXT, encode(message));
    }

    private static String templateOf(WhatsAppLinkStyle style, String customTemplate) {
        if (style != WhatsAppLinkStyle.CUSTOM) {
            return style.template();
        }
        if (customTemplate == null || customTemplate.isBlank()) {
            throw new IllegalArgumentException(I18n.get("error.whatsapp.templateEmpty"));
        }
        return customTemplate.trim();
    }

    /**
     * ترميز نص الرسالة ليصلح داخل رابط.
     *
     * <p>المسافة تُرمَّز {@code %20} لا {@code +}: علامة الزائد تعني مسافة في نطاق
     * الاستعلام وحده، ونمط {@code whatsapp://} يفتحه معالج البروتوكول في نظام التشغيل
     * لا المتصفح، فتصل المسافات فيه علاماتِ زائد داخل نص الرسالة.</p>
     */
    private static String encode(String message) {
        return URLEncoder.encode(message == null ? "" : message, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
