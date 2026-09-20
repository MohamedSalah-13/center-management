package com.codejava.center.web;

import com.codejava.center.core.secret.BackupSecretStore;
import com.codejava.center.core.secret.MessagingSecretStore;
import org.springframework.core.env.Environment;

/**
 * أسرار الخادم من بيئة التشغيل، لا من سجلّ ويندوز.
 *
 * <p>{@code MachineSecret} على الجهاز يُعتّم القيمة في السجلّ بمفتاح مشتقّ من اسم
 * المستخدم واسم النظام - والوثيقة تقول بصراحة إنه تعتيمٌ لا تشفير: يمنع من يقرأ
 * السجلّ بعينه، ولا يمنع من يشغّل برنامجاً بحساب ذلك المستخدم. وهو كافٍ هناك لأن من
 * يبلغ ذلك الحدّ يبلغ القاعدة نفسها.</p>
 *
 * <p>وعلى خادم لا سجلّ ويندوز أصلاً، ولا يصحّ الحلّ نفسه: الأسرار تُحقن من خارج
 * البرنامج فلا تدخل صورةَ النشر ولا مستودع الكود، وتُبدَّل بإعادة تشغيل لا بإصدار.
 * متغيّرات البيئة هي أبسط شكل لذلك، وهي نفسها ما يملؤه مخزنُ أسرارٍ حقيقي حين
 * يُركَّب - فلا يتغيّر هذا الصنف حينها بل مصدرُ ما يقرؤه.</p>
 *
 * <p><b>ولا افتراض عند الغياب.</b> كلمةُ مرورٍ فارغة تُشفّر بها النسخ تعني نسخاً
 * مفتوحة يظنّها صاحبها مغلقة، ورمزُ مراسلةٍ فارغ يعني أربعين طلباً يردّها المزوّد
 * بـ 401. الغياب يُقال صراحةً - {@code null} و{@code false} - ويقرؤه
 * {@code configurationProblem} فيقول للشاشة ما ينقص قبل أول إرسال.</p>
 */
public class EnvironmentSecrets implements BackupSecretStore, MessagingSecretStore {

    /** كلمة مرور تشفير النسخ الاحتياطية */
    static final String BACKUP_PASSPHRASE = "CENTER_BACKUP_PASSPHRASE";

    /** رمز مزوّد الرسائل */
    static final String MESSAGING_TOKEN = "CENTER_MESSAGING_TOKEN";

    private final Environment environment;

    public EnvironmentSecrets(Environment environment) {
        this.environment = environment;
    }

    /**
     * التشفير مفعَّل حين توجد كلمة مرور، لا بمفتاح ثانٍ يُضبط بجوارها.
     *
     * <p>مفتاحان يعني حالةً رابعة بلا معنى: "مفعَّل بلا كلمة" - وهي التي تُسقط النسخة
     * الليلية كل ليلة عند من ظنّ أنه ضبط الاثنين.</p>
     */
    @Override
    public boolean encryptionEnabled() {
        return passphrase() != null;
    }

    @Override
    public char[] passphrase() {
        return chars(BACKUP_PASSPHRASE);
    }

    @Override
    public char[] apiToken() {
        return chars(MESSAGING_TOKEN);
    }

    /**
     * {@code char[]} لا {@code String} عن قصد - وهو عقد الواجهة: النصّ يبقى في مجمّع
     * الثوابت حتى ينظّفه جامعُ المهملات، والمصفوفة تُمحى بعد الاستعمال.
     */
    private char[] chars(String name) {
        String value = environment.getProperty(name);
        return value == null || value.isBlank() ? null : value.toCharArray();
    }
}
