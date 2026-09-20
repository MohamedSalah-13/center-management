package com.codejava.center.service.notification;

/**
 * شكل رابط واتساب كما هو مضبوط على الطرف الذي يفتح الرابط.
 *
 * <p>ليس سرّاً فلا مكان له في {@code MessagingSecretStore}، وليس في النواة لأن
 * {@link WhatsAppLinkStyle} نوعٌ من طبقة الأعمال والنواة لا تعرف قنوات الإرسال أصلاً.
 * لكنه مثل السرّ في شيء واحد: <b>خاصية جهازٍ لا سياسة سنتر</b> — {@code whatsapp://}
 * يفتح التطبيق على جهاز مثبَّت عليه ويفشل على غيره، تماماً كالطابعة الموصولة به.</p>
 *
 * <p>ولذلك لا يقرأها {@link NotificationConfigProvider} من {@code java.util.prefs}
 * مباشرةً: خادم SaaS لا سجلّ ويندوز له، وقناة الرابط لا معنى لها عنده أصلاً فيعيد
 * الافتراضي.</p>
 */
public interface MessagingLinkPreferences {

    /** النمط المضبوط؛ لا يعود {@code null} — الافتراضي {@link WhatsAppLinkStyle#WA_ME} */
    WhatsAppLinkStyle linkStyle();

    /** قالب الرابط المكتوب يدوياً لنمط {@link WhatsAppLinkStyle#CUSTOM}، أو {@code null} */
    String linkTemplate();
}
