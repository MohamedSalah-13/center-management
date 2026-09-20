-- =====================================================================
-- المخطط الأولي لنظام إدارة السنتر
--
-- مولَّد من الكيانات نفسها عبر SchemaScriptGenerator في مصادر الاختبار،
-- لا مكتوب يدوياً: ddl-auto=validate يرفض بدء التشغيل عند أي اختلاف بين
-- المخطط والكيانات، وكتابته يدوياً تعني اكتشاف الفروق عند العميل لا هنا.
--
-- لا تعدّل هذا الملف بعد تطبيقه على أي قاعدة بيانات. أي تغيير لاحق
-- يكون في ملف V جديد.
-- =====================================================================

create table teachers (
    id bigint not null auto_increment,
    name varchar(100) not null,
    subject varchar(50) not null,
    commission_type varchar(20) not null,
    commission_value decimal(12,2) not null,
    primary key (id)
) engine=InnoDB;

create table students (
    id bigint not null auto_increment,
    barcode varchar(50) not null,
    name varchar(100) not null,
    phone varchar(15),
    parent_phone varchar(15),
    school_level varchar(50),
    is_active bit not null,
    primary key (id)
) engine=InnoDB;

create table users (
    id bigint not null auto_increment,
    username varchar(50) not null,
    password varchar(255) not null,
    role enum ('ADMIN','SECRETARY') not null,
    primary key (id)
) engine=InnoDB;

create table center_settings (
    id bigint not null,
    center_name varchar(255),
    center_phone varchar(255),
    logo_path varchar(255),
    backup_path varchar(255),
    auto_backup_enabled bit not null,
    ledger_start_date date,
    primary key (id)
) engine=InnoDB;

create table course_groups (
    id bigint not null auto_increment,
    name varchar(100) not null,
    teacher_id bigint not null,
    max_capacity integer,
    session_price decimal(12,2) not null,
    primary key (id)
) engine=InnoDB;

create table student_groups (
    id bigint not null auto_increment,
    student_id bigint not null,
    group_id bigint not null,
    join_date date not null,
    is_active bit not null,
    primary key (id)
) engine=InnoDB;

create table sessions (
    id bigint not null auto_increment,
    group_id bigint not null,
    session_date date not null,
    is_active bit not null,
    is_paid_out bit not null,
    primary key (id)
) engine=InnoDB;

create table attendances (
    id bigint not null auto_increment,
    student_id bigint not null,
    session_id bigint not null,
    time_in datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table transactions (
    id bigint not null auto_increment,
    type enum ('INCOME','SESSION_CHARGE','EXPENSE','TEACHER_PAYOUT') not null,
    amount decimal(12,2) not null,
    description varchar(255) not null,
    transaction_date datetime(6) not null,
    student_id bigint,
    group_id bigint,
    session_id bigint,
    primary key (id)
) engine=InnoDB;

create table notification_logs (
    id bigint not null auto_increment,
    student_id bigint not null,
    type enum ('ABSENCE','ARREARS') not null,
    recipient_phone varchar(20) not null,
    message varchar(500) not null,
    sent_at datetime(6) not null,
    channel varchar(30) not null,
    primary key (id)
) engine=InnoDB;

-- القيود الفريدة والفهارس
alter table students add constraint idx_student_barcode unique (barcode);
alter table students add constraint UK_2ywwufkbld4t2tgs7rqf3c8u4 unique (name);
alter table users add constraint UK_r43af9ap4edm43mmtq01oddj6 unique (username);
create index idx_notification_student_type on notification_logs (student_id, type);

-- المفاتيح الأجنبية
alter table course_groups add constraint FK6sm95l7jyidd39ytns4bdpjmp
    foreign key (teacher_id) references teachers (id);

alter table student_groups add constraint FK2lrixbmu6gchpunl625qiqmc3
    foreign key (student_id) references students (id);
alter table student_groups add constraint FKiyx15krwyj619otc7oi1gsemj
    foreign key (group_id) references course_groups (id);

alter table sessions add constraint FKg7h83aj343mccb1103d7vsf79
    foreign key (group_id) references course_groups (id);

alter table attendances add constraint FK7bm4q4wptspkenhrsjgatdmk0
    foreign key (student_id) references students (id);
alter table attendances add constraint FK24t12atd4p5tqm2227iqfm11y
    foreign key (session_id) references sessions (id);

alter table transactions add constraint FK79fflsn23798fyh9hp8h66xkn
    foreign key (student_id) references students (id);
alter table transactions add constraint FK8u69lgub767v40ju809ja5tqt
    foreign key (group_id) references course_groups (id);
alter table transactions add constraint FK2jpsuhpiv652pbaft4wfl14y1
    foreign key (session_id) references sessions (id);

alter table notification_logs add constraint FK52bhkwdkv9axm768witwu2jqb
    foreign key (student_id) references students (id);
