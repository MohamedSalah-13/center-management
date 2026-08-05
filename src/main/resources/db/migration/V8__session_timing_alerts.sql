-- =====================================================================
-- نوعا تنبيه جديدان يخصّان مواعيد الحصص:
--
--   SESSION_STARTING_SOON  اقترب موعد مجموعة اليوم ولم تُفتح لها حصة بعد
--   SESSION_ENDING_SOON    حصة مفتوحة اقترب - أو مضى - موعد انتهائها
--
-- صارا ممكنَين لأن CourseGroup صار يحمل أيام الانعقاد وساعتَي البداية والنهاية
-- (V6). قبلها لم يكن في النظام ما يُستنتج منه "موعد الحصة" أصلاً.
--
-- ولا جدول جديد ولا عمود: النوعان يستعملان threshold القائم - عدد الدقائق قبل
-- الموعد - وسطورهما تُكتب في جدول alerts نفسه. كل ما يلزم هو توسيع تعدادات
-- الأعمدة، وهي ثلاثة لأن AlertType يظهر في ثلاثة جداول.
--
-- alert_rules و alerts يستقبلان النوعين فعلاً. أما notification_logs فلا يُكتب
-- فيه أيٌّ منهما أبداً - كلاهما داخليّ لا يُرسل إلى هاتف أحد - لكن العمود يعكس
-- الـ enum كاملاً وإلا اختلف المخطط عن الكيان وفشل ddl-auto=validate عند الإقلاع.
--
-- ملاحظة: ADD COLUMN IF NOT EXISTS لهجة MariaDB وتفشل على MySQL، فلا تُستعمل.
-- =====================================================================

alter table alert_rules
    modify column type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_STARTING_SOON','SESSION_ENDING_SOON',
        'SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null;

alter table alerts
    modify column type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_STARTING_SOON','SESSION_ENDING_SOON',
        'SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null;

alter table notification_logs
    modify column type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_STARTING_SOON','SESSION_ENDING_SOON',
        'SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null;
