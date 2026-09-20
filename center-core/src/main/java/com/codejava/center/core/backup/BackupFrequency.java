package com.codejava.center.core.backup;

/**
 * كل كم تُؤخذ النسخة الاحتياطية التلقائية.
 *
 * <p>كان الموعد مكتوباً في الكود ({@code @Scheduled(cron = "0 0 2 * * ?")}) فلا يملك
 * السنتر تغييره: مركز يفتح حتى منتصف الليل قد يريدها الرابعة فجراً، وآخر يفضّل نسخة
 * أسبوعية على قرص خارجي يوصله يوم الجمعة.</p>
 *
 * <p>لا {@code getDisplayName()} هنا كبقية الثوابت المعروضة: الثابت في النواة، والنواة
 * لا تعرف لغةً ولا حزمة رسائل. الاسم يُترجَم في الشاشة التي تعرضه، ومفاتيحه مضمونة
 * بحلقة {@code backupFrequency.*} في {@code MessageBundleTest}.</p>
 */
public enum BackupFrequency {
    DAILY,
    WEEKLY,
    MONTHLY
}
