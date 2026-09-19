package com.codejava.center.service;

import com.codejava.center.util.I18n;

import java.nio.file.Path;

/**
 * ما تمّ في عملية نسخ احتياطي واحدة: الملف المكتوب، وما حُذف من القديم معه.
 *
 * @param file   النسخة التي كُتبت للتوّ
 * @param pruned حصيلة حذف النسخ الزائدة عن العدد المحفوظ
 */
public record BackupOutcome(Path file, BackupRetention.Pruned pruned) {

    /**
     * جملة تُعرض بعد النسخة، بلغة الواجهة.
     *
     * <p>تُبنى هنا لا في الشاشة لأن شاشتين تعرضانها: تبويب النسخ الاحتياطي في الإعدادات،
     * واختصار لوحة المفاتيح الذي يُضغط من أي شاشة. وثانيهما نسخة من أولهما، وثالثة كانت
     * ستنسخ الثانية - نفس سبب وجود {@code Sheets.show}.</p>
     *
     * <p>الصمت عند {@code deleted == 0} مقصود: النتيجة الطبيعية في مجلد لم يبلغ العدد بعد
     * هي ألا يُحذف شيء، وسطرٌ يقول "حُذفت صفر نسخة" بعد كل نسخة يُقرأ كأنه عطل.</p>
     */
    public String describe() {
        StringBuilder text = new StringBuilder(I18n.format("settings.backupDone", file));
        if (pruned.deleted() > 0) {
            text.append(System.lineSeparator())
                    .append(I18n.format("settings.backupPruned", pruned.deleted()));
        }
        // الفشل يُقال دائماً ولو حُذف غيره: مجلد على قرص شبكة صار للقراءة فقط يبدأ هكذا،
        // وبلا هذا السطر يمتلئ شهوراً والشاشة تقول "تمت النسخة" في كل مرة
        if (pruned.failed() > 0) {
            text.append(System.lineSeparator())
                    .append(I18n.format("settings.backupPruneFailed", pruned.failed()));
        }
        if (pruned.unreadable()) {
            text.append(System.lineSeparator()).append(I18n.get("settings.backupPruneUnreadable"));
        }
        return text.toString();
    }
}
