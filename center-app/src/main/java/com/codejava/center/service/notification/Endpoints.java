package com.codejava.center.service.notification;

import com.codejava.center.core.net.OutboundUrl;
import com.codejava.center.util.I18n;

import java.util.Optional;

/**
 * العنوان الذي يُرسل إليه البرنامج رمزَ السنتر، مفحوصاً قبل أن يُفتح.
 *
 * <p>الفحص في موضعين لأن لكلٍّ منهما سؤالاً مختلفاً:</p>
 *
 * <ul>
 *   <li>{@link #problemWith} تسأل عن <b>القالب</b> كما كُتب في الشاشة، فتقول لا قبل
 *       الحفظ - لا بعد أن يُرسَل الرمز إلى حيث لا يجب.</li>
 *   <li>{@link #require} تسأل عن <b>العنوان النهائي</b> بعد ملء المواضع، وهي الحدّ
 *       الفعلي: قيمةٌ حُفظت قبل هذا الفحص، أو قاعدةٌ عُدّلت من خارج البرنامج، لا تمرّ.</li>
 * </ul>
 *
 * <p>وموضع {@code {token}} يُفحص على القالب وحده: بعد الملء يصير الرمزُ نصّاً عادياً
 * في العنوان ولا يبقى ما يدلّ على أنه رمز - وهو تحديداً ما يجعله يظهر في سجلّ كل
 * وسيط بين هنا وهناك.</p>
 */
final class Endpoints {

    private Endpoints() {
    }

    /** ما يُعرض في الشاشة إن كان العنوان غير مقبول، أو لا شيء */
    static Optional<String> problemWith(String template) {
        if (template == null || template.isBlank()) {
            return Optional.empty();
        }
        if (template.contains(HttpGatewaySender.TOKEN)) {
            return Optional.of(I18n.get("error.notification.tokenInUrl"));
        }
        try {
            OutboundUrl.of(withoutPlaceholders(template));
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(I18n.get("error.notification.urlNotAllowed"));
        }
    }

    /**
     * العنوان النهائي أو استثناء.
     *
     * <p>يُرمى ولا يُعاد فارغاً: إرسالٌ يُلغى بصمت يُقرأ على أنه نجح.</p>
     */
    static String require(String filledUrl) {
        try {
            return OutboundUrl.of(filledUrl).asString();
        } catch (RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.notification.urlNotAllowed"), e);
        }
    }

    /**
     * يستبدل المواضع بنصٍّ بريء ليصير القالب عنواناً يمكن تحليله.
     *
     * <p>الأقواس المعقوفة غير مسموحة في عنوان، فقالبٌ يحمل {@code {phone}} لا يُحلَّل
     * أصلاً - ولو رُفض لذلك لَرُفض كل قالب صحيح. والمستبدَل نصٌّ لا يغيّر المضيف ولا
     * المخطط، وهما وحدهما ما يُفحص.</p>
     */
    private static String withoutPlaceholders(String template) {
        return template.trim()
                .replace(HttpGatewaySender.PHONE, "0")
                .replace(HttpGatewaySender.MESSAGE, "x")
                .replace(HttpGatewaySender.SENDER, "x");
    }
}
