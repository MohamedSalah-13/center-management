package com.codejava.center.service.notification;

/**
 * منفذ إرسال الرسائل، مستقل عن المزوّد.
 *
 * <p>اختيار مزوّد الرسائل قرار تجاري له تكلفة وإجراءات تسجيل، ولا يجوز أن يتسرّب
 * إلى منطق تحديد من يُبلَّغ وبأي نص. لذلك كل ما يخص المزوّد محصور خلف هذه الواجهة،
 * وإضافة WhatsApp Business API أو بوابة SMS لاحقاً تعني صنفاً جديداً يطبّقها
 * دون لمس {@code NotificationService} ولا الشاشة.</p>
 */
public interface MessageSender {

    /**
     * @param internationalPhone الرقم بالصيغة الدولية (مثال: 201012345678)
     * @param message            نص الرسالة
     * @return نتيجة المحاولة
     */
    SendResult send(String internationalPhone, String message);

    /** اسم القناة كما يُسجَّل في سجل الإشعارات */
    String channelName();

    /**
     * هل تتطلب القناة تدخّل المستخدم لكل رسالة؟
     * القنوات اليدوية (مثل فتح محادثة واتساب) لا تصلح للإرسال الجماعي الصامت،
     * والشاشة تستخدم هذه المعلومة لتحذير المستخدم قبل إرسال دفعة كبيرة.
     */
    boolean requiresManualConfirmation();

    record SendResult(boolean success, String failureReason) {
        public static SendResult ok() {
            return new SendResult(true, null);
        }

        public static SendResult failed(String reason) {
            return new SendResult(false, reason);
        }
    }
}
