package com.codejava.center.service;

import com.codejava.center.core.backup.BackupRetention;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * الجملة التي تُعرض بعد نسخةٍ احتياطية: ما تقوله، وما تسكت عنه.
 *
 * <p>وهي قرارُ <b>ما يُقال</b> لا مجرّد تنسيق. سطرٌ بعد كل نسخة يقول "حُذفت صفر نسخة"
 * أو "رُفعت بنجاح" يُقرأ مرتين ثم لا يُقرأ - وفي الصمت الذي يصنعه يضيع السطرُ الوحيد
 * الذي كان يجب أن يُرى.</p>
 *
 * <p>ولا نصَّ عربياً في التوقّعات: اللغة تُحفظ لكل جهاز، ومقارنةٌ بنصٍّ مكتوب تسقط
 * يوم يبدّلها أحد.</p>
 */
class BackupOutcomeTest {

    private static final Path FILE = Path.of("D:", "backups", "backup_2026-09-21_02-00-00.sql.enc");

    /** الحالة الطبيعية: نسخةٌ كُتبت، ولا شيء حُذف، وخرجت - سطرٌ واحد لا أكثر */
    @Test
    void aBackupThatWentWellSaysOneLineAndNothingElse() {
        String said = describe(new BackupRetention.Pruned(0, 0, false), OffsiteCopy.stored("https://store/x"));

        assertThat(said).isEqualTo(I18n.format("settings.backupDone", FILE));
    }

    /**
     * <b>ونسخةٌ لم تخرج من الجهاز تقول ذلك.</b>
     *
     * <p>هذا هو السطر كلُّه: الملفُّ مكتوبٌ فعلاً، فلا شيء يبدو خاطئاً - وهو على القرص
     * نفسه الذي يحمل القاعدة. بدون السطر تُقرأ الشاشةُ "تمت النسخة" ويبقى الظنُّ أن
     * نسخةً في مأمنٍ خارج الجهاز، والظنُّ هو ما يمنع السؤال.</p>
     */
    @Test
    void aBackupThatNeverLeftTheMachineSaysSo() {
        String problem = I18n.get("error.offsite.tokenMissing");
        String said = describe(new BackupRetention.Pruned(0, 0, false), OffsiteCopy.failed(problem));

        assertThat(said)
                .contains(I18n.format("settings.backupDone", FILE))
                .contains(I18n.format("settings.backupNotOffsite", problem));
    }

    /** ولا يُقال شيء حين لا وجهةَ أصلاً: قرارٌ لا عطل */
    @Test
    void aBackupWithNoOffsiteStoreConfiguredSaysNothingAboutIt() {
        String said = describe(new BackupRetention.Pruned(0, 0, false), OffsiteCopy.notRequested());

        assertThat(said).isEqualTo(I18n.format("settings.backupDone", FILE));
    }

    private static String describe(BackupRetention.Pruned pruned, OffsiteCopy offsite) {
        return new BackupOutcome(FILE, pruned, offsite).describe();
    }
}
