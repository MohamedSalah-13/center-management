-- =====================================================================
-- سجل المراقبة: من فعل ماذا ومتى.
--
-- الجدول مأخوذ من target/schema-mysql.sql الذي يولّده SchemaScriptGenerator
-- من الكيانات، لا مكتوب يدوياً: ddl-auto=validate يرفض بدء التشغيل عند أي
-- اختلاف بين المخطط والكيان.
--
-- لا مفتاح أجنبي إلى users رغم وجود actor_username: المفتاح الأجنبي كان
-- سيمنع حذف المستخدم أو يحذف أثره معه، وحذف الحساب هو أول ما يفعله من يريد
-- إخفاء تصرّفاته. الاسم هنا نسخة وقت الحدث لا إشارة إلى صف قائم.
--
-- الفهرسان يخدمان تصفية الشاشة (فترة زمنية، ثم مستخدم). الجدول ينمو بلا حدّ
-- ولا يُنظَّف: تنظيفه يعني حذف أدلة، وهو نقيض غرضه.
--
-- ملاحظة: ADD COLUMN IF NOT EXISTS لهجة MariaDB وتفشل على MySQL، فلا تُستعمل.
-- =====================================================================

create table audit_logs (
    id bigint not null auto_increment,
    occurred_at datetime(6) not null,
    actor_username varchar(50),
    actor_role enum ('ADMIN','SECRETARY'),
    action enum (
        'LOGIN_SUCCEEDED','LOGIN_FAILED','LOGGED_OUT','ACCESS_DENIED',
        'USER_CREATED','USER_UPDATED','USER_DELETED',
        'PAYMENT_RECORDED','EXPENSE_RECORDED','TEACHER_PAYOUT_PAID',
        'STUDENT_CREATED','STUDENT_UPDATED','STUDENT_DELETED','STUDENT_ENROLLED',
        'GROUP_CREATED','GROUP_UPDATED','GROUP_DELETED',
        'TEACHER_CREATED','TEACHER_UPDATED','TEACHER_DELETED',
        'SESSION_OPENED','SESSION_CLOSED',
        'SETTINGS_UPDATED','BACKUP_CREATED','BACKUP_RESTORED'
    ) not null,
    entity_id bigint,
    entity_label varchar(150),
    amount decimal(12,2),
    details varchar(500),
    successful bit not null,
    primary key (id)
) engine=InnoDB;

create index idx_audit_occurred_at on audit_logs (occurred_at);
create index idx_audit_actor on audit_logs (actor_username);
