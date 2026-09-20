-- =====================================================================
-- جدولة النسخ الاحتياطي: موعد يختاره السنتر بدل الموعد المكتوب في الكود.
--
-- الفروق مأخوذة من target/schema-mysql.sql الذي يولّده SchemaScriptGenerator
-- من الكيانات، لا مكتوبة يدوياً.
--
-- كل الأعمدة تقبل NULL عن قصد: قاعدة بيانات قائمة لن تحمل قيمة لأيٍّ منها،
-- و BackupSchedule يملأ الناقص بالموعد القديم نفسه (يومياً 2 صباحاً) فلا
-- يتغيّر سلوك سنتر رقّى البرنامج ولم يفتح شاشة الإعدادات.
--
-- ملاحظة: ADD COLUMN IF NOT EXISTS لهجة MariaDB وتفشل على MySQL، فلا تُستعمل.
-- =====================================================================

alter table center_settings
    add column backup_frequency enum ('DAILY','WEEKLY','MONTHLY'),
    add column backup_time time(6),
    add column backup_day_of_week integer,
    add column backup_day_of_month integer,
    add column backup_retention_count integer,
    add column last_auto_backup_at datetime(6);
