-- =====================================================================
-- نظام التنبيهات: سجل ما يُكتشف، وضبط ما يُكتشف أصلاً.
--
-- كل ما هنا مأخوذ من target/schema-mysql.sql الذي يولّده SchemaScriptGenerator
-- من الكيانات، لا مكتوب يدوياً: ddl-auto=validate يرفض بدء التشغيل عند أي
-- اختلاف بين المخطط والكيان، فمخطط مكتوب باليد يفشل عند العميل لا هنا.
--
-- ملاحظة: ADD COLUMN IF NOT EXISTS لهجة MariaDB وتفشل على MySQL، فلا تُستعمل.
-- =====================================================================

-- ------------------------------------------------------------------
-- 1) قواعد التنبيهات: صف لكل نوع، يُكتب أول مرة يعدّله فيها المدير.
--
-- لا تُزرع صفوف هنا عن قصد. زرعُها كان يعني أن كل نوع يُضاف في نسخة لاحقة
-- يحتاج ترحيله الخاص ليزرع صفّه، وأن نسيان ذلك يترك نوعاً يظهر في الشاشة
-- بلا ضبط ولا يعمل. AlertRuleRegistry يبني الناقص من AlertType عند القراءة،
-- فالكود وحده مصدر القائمة.
-- ------------------------------------------------------------------
create table alert_rules (
    id bigint not null auto_increment,
    type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null,
    enabled bit not null,
    audience enum ('INTERNAL','PARENTS','BOTH') not null,
    severity enum ('CRITICAL','WARNING','INFO') not null,
    threshold integer,
    window_days integer,
    cooldown_days integer,
    updated_at datetime(6),
    updated_by varchar(50),
    primary key (id)
) engine=InnoDB;

alter table alert_rules add constraint uk_alert_rule_type unique (type);

-- ------------------------------------------------------------------
-- 2) صندوق التنبيهات.
--
-- dedupe_key وقيده الفريد هما جوهر الجدول لا تفصيل فيه: السنتر فيه أكثر من
-- جهاز، وكلٌّ منها يشغّل مجدوِله الخاص، فيصحو ثلاثتها على موعد الفحص نفسه.
-- بلا هذا القيد يُكتب التنبيه الواحد ثلاث مرات وتُرسل الرسالة ثلاثاً. والقيد
-- في القاعدة لا في الكود لأن بين الفحص والإدراج فجوةً يمرّ منها جهازان بالضبط.
--
-- ولا مفتاح أجنبي من entity_id إلى students أو course_groups: التنبيه يبقى
-- مقروءاً بعد حذف صاحبه (entity_label يحمل اسمه وقت الإطلاق)، ومفتاحٌ أجنبي
-- كان سيمنع الحذف أو يمحو التنبيه معه. وهو نفس السبب في audit_logs.
--
-- args وسائط الرسالة محايدة لغوياً، لا جملة جاهزة: الجملة تُبنى عند العرض
-- بلغة الواجهة، وتخزينها مترجَمة كان سيجمّد كل سطر على لغة الجهاز الذي أطلقه
-- فيظهر صندوق واحد بلغتين.
-- ------------------------------------------------------------------
create table alerts (
    id bigint not null auto_increment,
    type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null,
    severity enum ('CRITICAL','WARNING','INFO') not null,
    raised_at datetime(6) not null,
    entity_id bigint,
    entity_label varchar(150),
    args varchar(500),
    dedupe_key varchar(120) not null,
    acknowledged_at datetime(6),
    acknowledged_by varchar(50),
    primary key (id)
) engine=InnoDB;

alter table alerts add constraint uk_alert_dedupe unique (dedupe_key);
create index idx_alert_raised_at on alerts (raised_at);
create index idx_alert_acknowledged on alerts (acknowledged_at);

-- ------------------------------------------------------------------
-- 3) مفتاح التنبيهات الرئيسي وموعد الفحص، في إعدادات السنتر.
--
-- سياسة واحدة للسنتر كله لا تفضيل جهاز، تماماً كموعد النسخ الاحتياطي: لو كانت
-- على كل جهاز لَفحصت ثلاثة أجهزة في ثلاثة مواعيد واختلف ما يراه الموظف على
-- جهاز عمّا يراه على آخر.
--
-- alerts_enabled يبدأ صفراً: الترقية يجب ألا تجعل البرنامج يبدأ من نفسه ملء
-- صندوق لم يطلبه أحد - ولا، وهو الأهمّ، مراسلة أولياء الأمور عن مال. التشغيل
-- قرار يُتخذ من الشاشة.
-- ------------------------------------------------------------------
alter table center_settings
    add column alerts_enabled bit not null default 0,
    add column alert_scan_time time(6),
    add column last_alert_scan_at datetime(6);

-- ------------------------------------------------------------------
-- 4) توحيد مفردات الأنواع.
--
-- NotificationType صار AlertType، وقيمتاه ABSENCE و ARREARS باقيتان باسميهما
-- بالضبط: الصفوف المكتوبة قبل هذه النسخة تُقرأ كما هي بلا ترحيل بيانات، ولم
-- يتغيّر إلا اتساع العمود ليقبل أسماء الأنواع الجديدة.
--
-- الأنواع الداخلية (فشل نسخة احتياطية مثلاً) لا تُكتب هنا أبداً - لا تُرسل إلى
-- هاتف أحد - لكنها مذكورة في التعداد لأن العمود يعكس الـ enum كاملاً وإلا
-- اختلف المخطط عن الكيان وفشل validate.
-- ------------------------------------------------------------------
alter table notification_logs
    modify column type enum (
        'ABSENCE','STUDENT_INACTIVE','ARREARS','LOW_BALANCE','PAYMENT_RECEIPT',
        'TEACHER_DUES_PENDING','SESSION_LEFT_OPEN','GROUP_WITHOUT_SESSION',
        'BACKUP_FAILED','BACKUP_OVERDUE','FAILED_LOGIN_BURST','MESSAGING_CHANNEL_DOWN'
    ) not null;

-- ------------------------------------------------------------------
-- 5) حدث جديد في سجل المراقبة: تعديل قاعدة تنبيه.
--
-- يُسجَّل لأن إيقاف تنبيه يجعل النظام يصمت عن حالة قائمة - "لم يصلني إشعار"
-- جوابه هناك - ولأن تحويل وجهته إلى أولياء الأمور يجعل البرنامج يراسلهم باسم
-- السنتر بلا ضغطة من موظف.
-- ------------------------------------------------------------------
alter table audit_logs
    modify column action enum (
        'LOGIN_SUCCEEDED','LOGIN_FAILED','LOGGED_OUT','ACCESS_DENIED',
        'USER_CREATED','USER_UPDATED','USER_DELETED',
        'PAYMENT_RECORDED','EXPENSE_RECORDED','TEACHER_PAYOUT_PAID',
        'STUDENT_CREATED','STUDENT_UPDATED','STUDENT_DELETED','STUDENT_ENROLLED',
        'GROUP_CREATED','GROUP_UPDATED','GROUP_DELETED',
        'TEACHER_CREATED','TEACHER_UPDATED','TEACHER_DELETED',
        'SESSION_OPENED','SESSION_CLOSED',
        'SETTINGS_UPDATED','BACKUP_CREATED','BACKUP_RESTORED',
        'ALERT_RULE_UPDATED'
    ) not null;
