package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * كل كم تُؤخذ النسخة الاحتياطية التلقائية.
 *
 * <p>كان الموعد مكتوباً في الكود ({@code @Scheduled(cron = "0 0 2 * * ?")}) فلا يملك
 * السنتر تغييره: مركز يفتح حتى منتصف الليل قد يريدها الرابعة فجراً، وآخر يفضّل نسخة
 * أسبوعية على قرص خارجي يوصله يوم الجمعة.</p>
 */
public enum BackupFrequency {
    DAILY,
    WEEKLY,
    MONTHLY;

    public String getDisplayName() {
        return I18n.get("backupFrequency." + name());
    }
}
