package com.codejava.center.core.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * عنوانٌ يكتبه صاحب السنتر ويُرسل إليه البرنامج رمزَه.
 *
 * <p>عنوان مزوّد الرسائل نصٌّ حرّ في شاشة الإعدادات، والبرنامج يفتحه من <b>داخل
 * الشبكة</b> حاملاً {@code Authorization: Bearer}. هذا هو شكل SSRF بالضبط: من يكتب
 * العنوان يجعل الخادم يطلب ما لا يستطيع هو طلبه - خدمةً داخلية، أو واجهةَ بيانات
 * المستضيف على {@code 169.254.169.254} التي تُعيد مفاتيح السحابة - والرمزُ يذهب معه.</p>
 *
 * <p>على جهازٍ في سنتر الأثر محدود: الشبكة الداخلية هي شبكة السنتر. وعلى خادمٍ يخدم
 * مئة سنتر، سطرٌ يكتبه أحدهم في شاشته يصل إلى جيران الخادم كلهم.</p>
 *
 * <h2>ثلاثة شروط، وكلُّها قوائم سماح</h2>
 *
 * <ul>
 *   <li><b>{@code https} وحدها.</b> رمزٌ يمشي على {@code http} يُقرأ في الطريق،
 *       و{@code file://} أو {@code gopher://} ليسا مزوّدَي رسائل.</li>
 *   <li><b>لا عناوين داخلية.</b> المضيف يُحلّ إلى عنوانه ويُرفض إن كان محلياً أو
 *       خاصاً أو من مدى الربط المحلي - وذاك الأخير هو موضع بيانات المستضيف.</li>
 *   <li><b>لا رمز في العنوان.</b> ما يُكتب في المسار أو في سلسلة الاستعلام يظهر في
 *       سجلّات كل وسيط بين هنا وهناك.</li>
 * </ul>
 *
 * <p>والتحقّق من العنوان الرقمي يقع عند الفحص لا عند الإرسال، وبينهما فجوة: اسمٌ
 * يُحلّ اليوم إلى عنوان عام وغداً إلى عنوان داخلي. سدُّها يحتاج تثبيت العنوان في
 * طبقة الاتصال نفسها، وهو ما لا يُكتب هنا. المذكور صراحةً لأنه حدُّ ما يحميه هذا
 * الصنف، لا لأنه لا يهمّ.</p>
 */
public final class OutboundUrl {

    private final URI value;

    /** خاصٌّ عن قصد: لا سبيل إلى بناء واحدٍ لم يمرّ على {@link #of} */
    private OutboundUrl(URI value) {
        this.value = value;
    }

    public URI value() {
        return value;
    }

    /**
     * @throws IllegalArgumentException برسالة تقول أيُّ الشروط سقط، لأن من يكتب
     *                                 العنوان هو من يصلحه
     */
    public static OutboundUrl of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("outbound url is required");
        }

        String trimmed = raw.trim();
        if (trimmed.contains("{token}")) {
            throw new IllegalArgumentException(
                    "the api token must not appear in the url: it is logged by every proxy on the way");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a valid url: " + e.getReason(), e);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new IllegalArgumentException("only https is allowed, not: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("the url has no host");
        }

        requirePublicHost(uri.getHost());
        return new OutboundUrl(uri);
    }

    /** أيصلح هذا النص عنواناً صادراً؟ لشاشةٍ تريد أن تقول لا قبل الحفظ */
    public static boolean isAllowed(String raw) {
        try {
            of(raw);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String asString() {
        return value.toString();
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * يرفض ما يشير إلى داخل الشبكة.
     *
     * <p><b>ومضيفٌ لا يُحلّ لا يُرفض لذلك.</b> رفضُه يبدو أشدّ وهو في الحقيقة عطل:
     * انقطاعُ DNS لدقيقة عند سنتر يوقف رسائل أولياء الأمور برسالة تقول "العنوان غير
     * مقبول" - وهي كذبة، فالعنوان مقبول والشبكة هي المقطوعة. والكلفة الأمنية لا شيء:
     * هذا الفحص يُعاد <b>عند كل إرسال</b> لا عند الحفظ وحده، فاسمٌ لم يُحلّ اليوم
     * ويشير غداً إلى الداخل يُرفض يوم يُحلّ - وقبل ذلك لا رمز يصل إلى أحد، لأن الطلب
     * نفسه لا يجد إلى أين يذهب.</p>
     *
     * <p>وعنوانٌ رقمي مكتوب صراحةً - {@code 10.0.0.5} أو {@code 169.254.169.254} - لا
     * يحتاج DNS أصلاً، وهو الشكل الأشيع لهذا الهجوم. فما يُفقد حين ينقطع DNS هو
     * الحالة الأبعد وحدها، ولدقائق.</p>
     */
    private static void requirePublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return;
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException(
                        "the url points inside the network, which is not where a provider lives: " + host);
            }
        }
    }
}
