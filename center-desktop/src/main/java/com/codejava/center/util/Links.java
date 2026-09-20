package com.codejava.center.util;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/**
 * فتح رابط بمعالج البروتوكول في نظام التشغيل.
 *
 * <p>هنا لا في {@code service/notification}: {@code WhatsAppLinkSender} كان يفتح الرابط
 * بنفسه، فكانت طبقة الأعمال تفترض سطحَ مكتبٍ وتطبيقاً مثبَّتاً عليه. صارت تبني الرابط
 * وتعيده، وهذا الصنف — على جهة الواجهة — هو من يملك جهازاً يفتحه فيه.</p>
 *
 * <p>يُستدعى من خيط خلفي ({@link FxAsync}) كما كان: الفتح يمرّ بمعالج النظام وقد يتأخر،
 * ولا نافذة JavaFX تُنشأ هنا.</p>
 */
public final class Links {

    private Links() {
    }

    /**
     * يفتح الرابط، ويرمي برسالة مترجَمة إن تعذّر.
     *
     * <p>{@link Desktop#browse} يمرّ عبر {@code ShellExecute} على ويندوز فيفتح
     * {@code whatsapp://} كما يفتح {@code https://}، لكنه غير مضمون على كل نسخة — وحين
     * يرفض، البديل هو نفس المعالج مستدعىً مباشرةً. بدون هذا البديل يظهر لمن اختار
     * "تطبيق واتساب" فشلٌ لا يفهم سببه بينما التطبيق مثبَّت أمامه.</p>
     *
     * @throws IllegalStateException برسالة مترجَمة حين يتعذّر الفتح
     */
    public static void open(String url) {
        URI uri = URI.create(url);
        try {
            openWithDesktop(uri);
        } catch (Exception e) {
            throw new IllegalStateException(I18n.format("error.link.openFailed", messageOf(e)), e);
        }
    }

    private static void openWithDesktop(URI uri) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
                return;
            } catch (Exception e) {
                if (!isWindows()) {
                    throw e;
                }
            }
        } else if (!isWindows()) {
            throw new UnsupportedOperationException(I18n.get("error.link.browserUnsupported"));
        }

        new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
