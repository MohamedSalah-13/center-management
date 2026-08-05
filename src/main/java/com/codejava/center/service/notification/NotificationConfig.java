package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;

/**
 * ضبط الإرسال كما هو الآن: نصف من إعدادات السنتر ونصف من تفضيلات الجهاز، مجموعين في
 * قيمة واحدة تُمرَّر إلى المرسل.
 *
 * <p>المرسلات لا تقرأ الإعدادات بنفسها عن قصد: قراءتها داخل كل مرسل تعني ضربة قاعدة
 * بيانات لكل رسالة في دفعة من مئة، وتجعل اختبار المرسل مستحيلاً بلا سياق Spring كامل.
 * {@link NotificationConfigProvider} يقرأ مرة، و{@link MessageSenderRouter} يمرّر.</p>
 *
 * @param channel     القناة المختارة
 * @param linkStyle   شكل رابط واتساب على هذا الجهاز
 * @param linkTemplate قالب الرابط المكتوب يدوياً (لنمط CUSTOM وحده)
 * @param apiUrl      عنوان المزوّد
 * @param senderId    معرّف المرسل عند المزوّد (رقم الواتساب أو الـ instance)
 * @param templateName اسم القالب المعتمَد عند Meta
 * @param templateLanguage رمز لغة القالب المعتمَد
 * @param bodyTemplate قالب جسم الطلب للمزوّد العام
 * @param apiToken    مفتاح الدخول المحفوظ على هذا الجهاز، أو {@code null}
 */
public record NotificationConfig(
        NotificationChannel channel,
        WhatsAppLinkStyle linkStyle,
        String linkTemplate,
        String apiUrl,
        String senderId,
        String templateName,
        String templateLanguage,
        String bodyTemplate,
        String apiToken) {

    /** لغة القالب حين لا تُضبط: رسائل السنتر عربية */
    public static final String DEFAULT_TEMPLATE_LANGUAGE = "ar";

    public String templateLanguageOrDefault() {
        return isBlank(templateLanguage) ? DEFAULT_TEMPLATE_LANGUAGE : templateLanguage.trim();
    }

    public boolean hasApiUrl() {
        return !isBlank(apiUrl);
    }

    public boolean hasSenderId() {
        return !isBlank(senderId);
    }

    public boolean hasToken() {
        return !isBlank(apiToken);
    }

    public boolean hasTemplateName() {
        return !isBlank(templateName);
    }

    public boolean hasBodyTemplate() {
        return !isBlank(bodyTemplate);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
