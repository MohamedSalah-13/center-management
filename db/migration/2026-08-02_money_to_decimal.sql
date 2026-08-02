-- =====================================================================
-- ترحيل أعمدة المبالغ من DOUBLE إلى DECIMAL(12,2)
-- التاريخ: 2026-08-02
--
-- ⚠ مهم: إعداد spring.jpa.hibernate.ddl-auto=update لا يغيّر نوع الأعمدة
--        الموجودة مسبقاً، لذلك يجب تنفيذ هذا السكربت يدوياً مرة واحدة
--        على أي قاعدة بيانات تحتوي بيانات قديمة.
--
-- قبل التنفيذ: خذ نسخة احتياطية كاملة
--     mysqldump -u <user> -p center_db > before_money_migration.sql
--
-- التنفيذ:
--     mysql -u <user> -p center_db < 2026-08-02_money_to_decimal.sql
-- =====================================================================

START TRANSACTION;

ALTER TABLE transactions
    MODIFY COLUMN amount DECIMAL(12,2) NOT NULL;

ALTER TABLE course_groups
    MODIFY COLUMN session_price DECIMAL(12,2) NOT NULL;

ALTER TABLE teachers
    MODIFY COLUMN commission_value DECIMAL(12,2) NOT NULL;

COMMIT;

-- =====================================================================
-- للتحقق بعد التنفيذ (يجب أن تظهر الأنواع decimal(12,2)):
--
--   SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
--   FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_SCHEMA = 'center_db'
--     AND COLUMN_NAME IN ('amount', 'session_price', 'commission_value');
--
-- ملاحظة: القيم المخزّنة سابقاً كـ DOUBLE قد تحمل انحرافاً بسيطاً
-- (مثل 99.99000000000001). التحويل إلى DECIMAL(12,2) يقرّبها إلى
-- أقرب قرشين، وهو السلوك المطلوب. راجع الأرصدة بعد الترحيل.
-- =====================================================================
