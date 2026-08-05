package com.codejava.center.service.notification;

import java.util.Optional;

/**
 * منفذ إرسال الرسائل، مستقل عن المزوّد.
 *
 * <p>اختيار مزوّد الرسائل قرار تجاري له تكلفة وإجراءات تسجيل، ولا يجوز أن يتسرّب
 * إلى منطق تحديد من يُبلَّغ وبأي نص. لذلك كل ما يخص المزوّد محصور خلف هذه الواجهة،
 * و{@code NotificationService} لا يعرف أي قناة تعمل الآن.</p>
 *
 * <p>يطبّقها {@link MessageSenderRouter} وحده: هو الذي يقرأ القناة المختارة عند كل
 * إرسال ويحوّل إلى {@link ChannelSender} المناسب. إضافة مزوّد جديد تعني
 * {@code ChannelSender} جديداً لا تطبيقاً جديداً لهذه الواجهة.</p>
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

    /**
     * ما ينقص القناة المختارة لتعمل، أو {@code Optional} فارغ إن كانت جاهزة.
     *
     * <p>موجودة لتقول شاشة الإعدادات "مفتاح الدخول غير مضبوط على هذا الجهاز" قبل موعد
     * الإرسال بيوم، بدل أن يكتشف المستخدم ذلك وهو أمام قائمة أربعين ولي أمر.</p>
     */
    Optional<String> configurationProblem();

    record SendResult(boolean success, String failureReason) {
        public static SendResult ok() {
            return new SendResult(true, null);
        }

        public static SendResult failed(String reason) {
            return new SendResult(false, reason);
        }
    }
}
