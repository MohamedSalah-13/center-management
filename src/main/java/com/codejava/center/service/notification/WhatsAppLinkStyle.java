package com.codejava.center.service.notification;

import com.codejava.center.util.I18n;

/**
 * شكل رابط واتساب الذي يفتحه الجهاز.
 *
 * <p>كان الرابط مكتوباً في الكود ({@code https://wa.me/...}) بلا سبيل لتغييره، وهو ليس
 * خياراً واحداً صالحاً للجميع:</p>
 *
 * <ul>
 *   <li>{@code wa.me} يمرّ بصفحة "متابعة إلى المحادثة" في المتصفح ثم يفتح واتساب ويب أو
 *       التطبيق — خطوة زائدة عند كل رسالة.</li>
 *   <li>{@code whatsapp://} يفتح تطبيق واتساب المثبَّت على الجهاز مباشرةً بلا متصفح،
 *       وهو الأسرع في سنتر يرسل عشرات الرسائل — لكنه يفشل إن لم يكن التطبيق مثبَّتاً.</li>
 * </ul>
 *
 * <p>ولهذا الاختيار <b>لكل جهاز</b> لا لكل سنتر: تثبيت واتساب سطح المكتب صفة الجهاز
 * الذي أمامه الموظف، تماماً كالطابعة الموصولة به.</p>
 */
public enum WhatsAppLinkStyle {

    /** الرابط القصير الرسمي؛ يفتح المتصفح ثم صفحة تأكيد قبل المحادثة */
    WA_ME("https://wa.me/{phone}?text={text}"),

    /** نفس خدمة واتساب بصيغتها الطويلة؛ بديل حين يحجب مزوّد الإنترنت النطاق القصير */
    API_WHATSAPP("https://api.whatsapp.com/send?phone={phone}&text={text}"),

    /** يفتح تطبيق واتساب المثبَّت مباشرةً بلا متصفح */
    DESKTOP_APP("whatsapp://send?phone={phone}&text={text}"),

    /** نصّ رابط يكتبه المستخدم، لواجهة أخرى أو نسخة معدَّلة من واتساب */
    CUSTOM(null);

    private final String template;

    WhatsAppLinkStyle(String template) {
        this.template = template;
    }

    /** قالب الرابط الجاهز، أو {@code null} للنمط الذي يكتب المستخدم قالبه بنفسه */
    public String template() {
        return template;
    }

    public String getDisplayName() {
        return I18n.get("whatsappLinkStyle." + name());
    }
}
