package com.codejava.center.core.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * العنوان الذي يكتبه صاحب السنتر ويطلبه الخادم من داخل الشبكة.
 *
 * <p>هذا هو شكل SSRF: من يكتب العنوان يجعل البرنامج يطلب ما لا يستطيع هو طلبه،
 * والرمزُ يذهب معه في الترويسة. ولا شيء في ذلك يبدو معطوباً.</p>
 */
class OutboundUrlTest {

    @Test
    void aPlainHttpsProviderIsAllowed() {
        assertThat(OutboundUrl.of("https://graph.facebook.com/v19.0/1234/messages").asString())
                .isEqualTo("https://graph.facebook.com/v19.0/1234/messages");
    }

    /** رمزٌ يمشي على http يُقرأ في الطريق */
    @Test
    void plainHttpIsRefused() {
        assertThatThrownBy(() -> OutboundUrl.of("http://example.com/send"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void schemesThatAreNotTheWebAreRefused() {
        assertThatThrownBy(() -> OutboundUrl.of("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrl.of("gopher://example.com/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** الشبكة الداخلية ليست موضع مزوّد رسائل */
    @Test
    void addressesInsideTheNetworkAreRefused() {
        assertThat(OutboundUrl.isAllowed("https://127.0.0.1/send")).isFalse();
        assertThat(OutboundUrl.isAllowed("https://localhost/send")).isFalse();
        assertThat(OutboundUrl.isAllowed("https://10.0.0.5/send")).isFalse();
        assertThat(OutboundUrl.isAllowed("https://192.168.1.1/send")).isFalse();
        assertThat(OutboundUrl.isAllowed("https://172.16.4.4/send")).isFalse();
    }

    /**
     * {@code 169.254.169.254} هي واجهة بيانات المستضيف على أكثر من سحابة، وما تعيده
     * مفاتيحُ الخادم نفسه. وهي أول ما يُجرَّب حين يوجد عنوانٌ حرّ.
     */
    @Test
    void theCloudMetadataAddressIsRefused() {
        assertThat(OutboundUrl.isAllowed("https://169.254.169.254/latest/meta-data/")).isFalse();
    }

    /** ما يُكتب في العنوان يظهر في سجلّات كل وسيط بين هنا وهناك */
    @Test
    void aTokenPlaceholderInTheUrlIsRefused() {
        assertThatThrownBy(() -> OutboundUrl.of("https://example.com/send?key={token}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    /**
     * انقطاعُ DNS ليس عنواناً ممنوعاً.
     *
     * <p>رفضُ ما لا يُحلّ يبدو أشدّ وهو عطل: دقيقةٌ بلا DNS توقف رسائل سنتر برسالة
     * تقول إن عنوانه ممنوع. والفحص يُعاد عند كل إرسال، فما يشير إلى الداخل يُرفض يوم
     * يُحلّ - وقبل ذلك لا يصل رمزٌ إلى أحد لأن الطلب لا يجد إلى أين يذهب.</p>
     */
    @Test
    void aHostThatDoesNotResolveIsNotRefusedForThatReason() {
        assertThat(OutboundUrl.isAllowed("https://no-such-host.invalid/send")).isTrue();
    }

    @Test
    void whatIsNotAUrlAtAllIsRefused() {
        assertThat(OutboundUrl.isAllowed(null)).isFalse();
        assertThat(OutboundUrl.isAllowed("   ")).isFalse();
        assertThat(OutboundUrl.isAllowed("not a url")).isFalse();
        assertThat(OutboundUrl.isAllowed("https://")).isFalse();
    }
}
