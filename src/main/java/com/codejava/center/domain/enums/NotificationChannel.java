package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * الطريقة التي تصل بها رسالة السنتر إلى ولي الأمر.
 *
 * <p>كانت القناة تُختار بخاصية في {@code application.properties} أي عند التثبيت لا عند
 * الاستعمال: تغييرها في نسخة jpackage يعني فتح ملف داخل مجلد البرنامج وإعادة تشغيله،
 * وهو ما لا يفعله صاحب سنتر. صارت اختياراً في شاشة الإعدادات محفوظاً في قاعدة البيانات.</p>
 *
 * <p>القيمة مخزَّنة في {@code center_settings} لأنها قرار السنتر لا قرار الجهاز: حساب
 * المزوّد واحد للسنتر كله، ولا معنى لأن يرسل جهاز عبر واتساب ويرسل الآخر عبر مزوّد.
 * ما يخصّ الجهاز فعلاً — شكل الرابط ومفتاح الدخول — في
 * {@link com.codejava.center.util.NotificationPreferences}.</p>
 */
public enum NotificationChannel {

    /**
     * فتح محادثة واتساب بالنص جاهزاً على هذا الجهاز، ويضغط الموظف "إرسال".
     * لا اشتراك ولا تكلفة، وتعمل بحساب واتساب عادي.
     */
    WHATSAPP_LINK(true),

    /** واتساب الرسمي من Meta (WhatsApp Business Cloud API): إرسال مباشر بلا تدخّل */
    WHATSAPP_CLOUD_API(false),

    /** مزوّد رسائل خارجي عبر طلب HTTP يُضبط نصّه في الإعدادات */
    HTTP_GATEWAY(false);

    private final boolean manual;

    NotificationChannel(boolean manual) {
        this.manual = manual;
    }

    /**
     * هل تحتاج القناة ضغطة من الموظف لكل رسالة؟
     * صفة القناة نفسها لا صفة ضبطها، ولهذا هي هنا لا في الإعدادات: الشاشة تحذّر قبل
     * دفعة كبيرة، لأن الإرسال بالرابط يفتح نافذة لكل ولي أمر.
     */
    public boolean requiresManualConfirmation() {
        return manual;
    }

    public String getDisplayName() {
        return I18n.get("notificationChannel." + name());
    }
}
