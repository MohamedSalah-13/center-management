package com.codejava.center.service.notification;

import com.codejava.center.util.I18n;

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

    /**
     * ما آلت إليه محاولة الإرسال، وهي <b>ثلاث</b> حالات لا اثنتان.
     *
     * <p>الثالثة هي {@link #handOff(String)}: قناةٌ لا يرسل فيها البرنامج شيئاً، بل يجهّز
     * رابطاً يفتحه إنسان أمام الشاشة ثم يضغط "إرسال" بيده. قناة رابط واتساب من هذا النوع،
     * وهي القناة الافتراضية. ولو جُمعت مع النجاح لكان معنى ذلك أن السطر يُكتب في
     * {@code notification_logs} قبل أن تُفتح المحادثة أصلاً — وسجلٌّ يقول إن ولي الأمر
     * رُوسل بينما لم تُفتح له نافذة هو بالضبط ما يمنع إعادة المحاولة.</p>
     *
     * <p>لذلك {@code handOff} <b>ليست نجاحاً</b>. من يستدعي يسأل {@link #needsHandOff()}
     * أولاً؛ ومن نسي يجد سبب فشل مكتوباً يقول إن المحادثة لم تُفتح، لا {@code null}.</p>
     *
     * @param success       تمّ الإرسال فعلاً بلا تدخّل أحد
     * @param failureReason سبب الفشل، أو سبب "لم تُفتح بعد" في حالة التسليم اليدوي
     * @param link          الرابط الذي على الواجهة فتحه، أو {@code null}
     */
    record SendResult(boolean success, String failureReason, String link) {
        public static SendResult ok() {
            return new SendResult(true, null, null);
        }

        public static SendResult failed(String reason) {
            return new SendResult(false, reason, null);
        }

        /** لا شيء أُرسل: الرابط جاهز وعلى الواجهة فتحه، ثم تسجيل ما حدث */
        public static SendResult handOff(String link) {
            return new SendResult(false, I18n.get("error.notification.linkNotOpened"), link);
        }

        public boolean needsHandOff() {
            return link != null;
        }
    }
}
