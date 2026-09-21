package com.codejava.center;

import com.codejava.center.core.backup.BackupTarget;
import com.codejava.center.core.backup.OffsiteBackup;
import com.codejava.center.core.print.SheetHeaderPolicy;
import com.codejava.center.core.secret.BackupSecretStore;
import com.codejava.center.core.secret.MessagingSecretStore;
import com.codejava.center.core.ui.UiDispatcher;
import com.codejava.center.service.notification.MessagingLinkPreferences;
import com.codejava.center.service.notification.WhatsAppLinkStyle;
import org.springframework.stereotype.Component;

/**
 * أجوبةُ الاختبارات عن منافذ النواة، كما يجيب عنها كل طرف بطريقته.
 *
 * <p>هذه الوحدة <b>مكتبة</b>: منافذها متروكة بلا تنفيذ عمداً، ويجيب عنها من يشغّلها -
 * الجهاز بسجلّ ويندوز وخيط JavaFX، والخادم ببيئة التشغيل وقاعدة المؤسسة. فحين تُقلع
 * اختباراتُها سياقاً كاملاً بلا طرفٍ خلفه لا يوجد من يجيب، وهو ما يجعل هذا الصنف
 * لازماً - تماماً كـ {@link TestActor}، وللسبب نفسه حرفاً: <b>الحدّ يعمل، وليس هذا
 * التفافاً عليه</b>.</p>
 *
 * <p>والأجوبة هنا أبسط ما يكون لأن لا اختبار يستعملها فعلاً: لا نسخة تُؤخذ، ولا رسالة
 * تُرسل. ما يُفحص هو أن السياق يُقلع، وأن bean ناقصاً يظهر هنا - على جهاز مطوّر بلا
 * Docker - لا في CI بعد دقيقتين، ولا عند من ينشر.</p>
 */
@Component
public class TestPorts implements BackupSecretStore, MessagingSecretStore, SheetHeaderPolicy,
        UiDispatcher, BackupTarget, OffsiteBackup, MessagingLinkPreferences {

    /** بلا تشفير: الغياب يُقال صراحةً، ولا كلمة مرور فارغة تُشفَّر بها نسخة */
    @Override
    public boolean encryptionEnabled() {
        return false;
    }

    @Override
    public char[] passphrase() {
        return null;
    }

    @Override
    public char[] apiToken() {
        return null;
    }

    @Override
    public boolean printsCenterHeader() {
        return true;
    }

    /** حيث وُلدت المهمّة: لا خيط واجهة في اختبار، وهو ما يجعل السلوك قابلاً للفحص */
    @Override
    public void dispatch(Runnable task) {
        task.run();
    }

    @Override
    public String host() {
        return "localhost";
    }

    @Override
    public String port() {
        return "3306";
    }

    @Override
    public String database() {
        return "center_test";
    }

    @Override
    public String username() {
        return "center_test";
    }

    @Override
    public String password() {
        return null;
    }

    @Override
    public String toolDirectory() {
        return "";
    }

    /**
     * لا وجهةَ خارج الجهاز: اختبارٌ يرفع ملفاً إلى الشبكة ليس اختباراً.
     *
     * <p>و{@code null} هنا يعني "لم يُطلب" لا "معطَّل"، فلا يُطلق تنبيهاً ولا يكتب
     * سطراً - وهو ما يُبقي كلّ اختبارٍ يأخذ نسخة على سلوكه قبل هذه الدفعة.</p>
     */
    @Override
    public String endpoint() {
        return null;
    }

    @Override
    public char[] token() {
        return null;
    }

    @Override
    public String folder() {
        return null;
    }

    @Override
    public WhatsAppLinkStyle linkStyle() {
        return WhatsAppLinkStyle.WA_ME;
    }

    @Override
    public String linkTemplate() {
        return WhatsAppLinkStyle.WA_ME.template();
    }
}
