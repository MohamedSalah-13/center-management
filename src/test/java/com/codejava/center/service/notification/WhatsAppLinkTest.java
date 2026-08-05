package com.codejava.center.service.notification;

import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * بناء رابط المحادثة.
 *
 * <p>الخطأ هنا لا يظهر في البرنامج بل عند ولي الأمر: رسالة مبتورة عند أول مسافة، أو
 * محادثة تُفتح بلا نص فيظنّ الموظف أنه أرسل. وكان الرابط مكتوباً في الكود بلا اختبار.</p>
 */
class WhatsAppLinkTest {

    private static final String PHONE = "201012345678";

    @Test
    void waMeLinkCarriesTheNumberAndTheEncodedText() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.WA_ME, null, PHONE, "hello world");

        assertThat(link).isEqualTo("https://wa.me/" + PHONE + "?text=hello%20world");
    }

    @Test
    void longFormLinkIsAnAlternativeToTheShortDomain() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.API_WHATSAPP, null, PHONE, "x");

        assertThat(link).isEqualTo("https://api.whatsapp.com/send?phone=" + PHONE + "&text=x");
    }

    @Test
    void desktopStyleUsesTheProtocolHandlerInsteadOfTheBrowser() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.DESKTOP_APP, null, PHONE, "x");

        assertThat(link).startsWith("whatsapp://send?phone=" + PHONE);
    }

    /**
     * علامة الزائد تعني مسافة في نطاق الاستعلام وحده، ونمط {@code whatsapp://} يفتحه
     * نظام التشغيل لا المتصفح — فتصل المسافات علاماتِ زائد داخل نصّ الرسالة نفسه.
     */
    @Test
    void spacesAreEncodedAsPercentTwentyNotAsPlus() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.DESKTOP_APP, null, PHONE, "a b c");

        assertThat(link).endsWith("text=a%20b%20c").doesNotContain("+");
    }

    @Test
    void arabicTextIsEncodedAsUtf8() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.WA_ME, null, PHONE, "سلام");

        // سين بالعربية = D8 B3 في UTF-8؛ حرف عربي غير مُرمَّز يصل مربعات فارغة
        assertThat(link).contains("%D8%B3").doesNotContain("سلام");
    }

    @Test
    void newLinesInTheMessageSurviveTheLink() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.WA_ME, null, PHONE, "أ\nب");

        assertThat(link).contains("%0A");
    }

    @Test
    void customTemplateIsUsedAsWritten() {
        String link = WhatsAppLink.build(WhatsAppLinkStyle.CUSTOM,
                "https://chat.example.com/?n={phone}&m={text}", PHONE, "hi");

        assertThat(link).isEqualTo("https://chat.example.com/?n=" + PHONE + "&m=hi");
    }

    /** قالب بلا موضع للرقم يفتح محادثة فارغة، وهو فشل صامت */
    @Test
    void rejectsACustomTemplateWithoutThePhonePlaceholder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WhatsAppLink.build(WhatsAppLinkStyle.CUSTOM,
                        "https://chat.example.com/?m={text}", PHONE, "hi"))
                .withMessage(I18n.format("error.whatsapp.templateMissing", WhatsAppLink.PHONE));
    }

    /** وقالب بلا موضع للنص يفتح محادثة بلا رسالة، فيظنّ الموظف أنه أرسل */
    @Test
    void rejectsACustomTemplateWithoutTheTextPlaceholder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WhatsAppLink.build(WhatsAppLinkStyle.CUSTOM,
                        "https://chat.example.com/?n={phone}", PHONE, "hi"))
                .withMessage(I18n.format("error.whatsapp.templateMissing", WhatsAppLink.TEXT));
    }

    @Test
    void rejectsAnEmptyCustomTemplate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WhatsAppLink.build(WhatsAppLinkStyle.CUSTOM, "  ", PHONE, "hi"))
                .withMessage(I18n.get("error.whatsapp.templateEmpty"));
    }
}
