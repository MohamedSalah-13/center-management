package com.codejava.center.core.secret;

/**
 * من أين يأتي مفتاح الدخول إلى مزوّد الرسائل.
 *
 * <p>نفس قاعدة {@link BackupSecretStore}: المفتاح سرّ، وحفظه في قاعدة بيانات السنتر
 * يعني خروجه داخل كل نسخة احتياطية — بجوار أرقام أولياء الأمور نفسها التي يصلح المفتاح
 * لمراسلتهم بها، فتصير فلاشةٌ ضائعة قدرةً على انتحال صفة السنتر.</p>
 *
 * <p>Desktop يحفظه معمّى لكل جهاز؛ والخادم يقرأه من خزنة أسرار لا من صفٍّ يظهر في أي
 * تصدير للبيانات.</p>
 */
public interface MessagingSecretStore {

    /**
     * المفتاح، أو {@code null} إن لم يُضبط.
     *
     * <p>{@code char[]} لا {@code String} لسبب {@link BackupSecretStore#passphrase()}
     * نفسه، والمستدعي هو من يمحوها.</p>
     */
    char[] apiToken();
}
