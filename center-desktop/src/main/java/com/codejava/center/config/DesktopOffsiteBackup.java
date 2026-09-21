package com.codejava.center.config;

import com.codejava.center.core.backup.OffsiteBackup;
import org.springframework.stereotype.Component;

/**
 * لا مستودعَ خارج هذا الجهاز، <b>لأن المسار نفسه هو المستودع</b>.
 *
 * <p>صاحبُ السنتر يكتب {@code backupPath} حيث يشاء، وما يكتبه فعلاً هو فلاشةٌ أو قرصُ
 * شبكة السنتر - وهو السببُ الذي جعل {@code BackupTarget.backupRoot} يجيب {@code null}
 * هنا: قرصُه قرصُه، وتقييدُه يمنع الموضعين اللذين تُكتب فيهما النسخ. فالنسخةُ تخرج من
 * الجهاز بقرارٍ منه، لا بميزةٍ في البرنامج.</p>
 *
 * <p>وعلى خادمٍ ينقلب ذلك: الملفُّ يجلس على القرص الذي يحمل قواعد السناتر كلّها، ولا
 * أحد يوجّهه إلى فلاشة. هناك يُضبط {@code ServerOffsiteBackup} من بيئة التشغيل.</p>
 *
 * <p><b>وهذا ليس امتناعاً.</b> رفعُ نسخةِ الجهاز إلى مستودعٍ للمالك ميزةٌ حقيقية -
 * جهازٌ يُسرق يأخذ النسخَ التي بجواره - لكنها ميزةٌ ثانية: حقلا عنوانٍ ورمزٍ في شاشة
 * الإعدادات وتفضيلاتُ جهازٍ لهما. فتُكتب حين تُطلب، لا تُقحم في دفعةٍ موضوعُها ثقبٌ
 * على الخادم.</p>
 */
@Component
public class DesktopOffsiteBackup implements OffsiteBackup {

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
}
