-- المواد قائمة مستقلة، والمعلم يرتبط بمعرف ثابت بدلاً من اسم حر.
create table subjects (id bigint not null auto_increment, name varchar(50) not null,
    name_key varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null, primary key (id)) engine=InnoDB;
alter table subjects add constraint uk_subject_name_key unique (name_key);
INSERT INTO subjects(name, name_key) VALUES
('اللغة الإنجليزية','english'),
('اللغة العربية','arabic'),
('الرياضيات','math'),
('العلوم','science'),
('فيزياء','custom:فيزياء'),
('كيمياء','custom:كيمياء'),
('أحياء','custom:احياء'),
('تاريخ','custom:تاريخ'),
('جغرافيا','custom:جغرافيا'),
('دراسات اجتماعية','custom:دراساتاجتماعيه'),
('اللغة الفرنسية','custom:اللغهالفرنسيه'),
('اللغة الألمانية','custom:اللغهالالمانيه'),
('حاسب آلي','custom:حاسبالي'),
('تربية دينية','custom:تربيهدينيه');
-- نسجل مفتاح كل مادة قديمة قبل إزالة عمود النص، ونحافظ على المواد المخصصة.
CREATE TEMPORARY TABLE subject_upgrade (
 teacher_id bigint not null primary key, name varchar(50) not null,
 name_key varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null);
INSERT INTO subject_upgrade(teacher_id,name,name_key)
SELECT id, CASE WHEN normalized = '' THEN 'غير محدد' ELSE TRIM(subject) END,
CASE
WHEN normalized IN ('e','english','انجليزي','انجليزيه','الانجليزيه','لغهانجليزيه','اللغهالانجليزيه') THEN 'english'
WHEN normalized IN ('arabic','عربي','عربيه','لغهعربيه','اللغهالعربيه') THEN 'arabic'
WHEN normalized IN ('math','maths','mathematics','رياضيات','الرياضيات') THEN 'math'
WHEN normalized IN ('science','علوم','العلوم') THEN 'science'
WHEN normalized = '' THEN 'custom:غيرمحدد'
ELSE CONCAT('custom:', normalized) END
FROM (SELECT id, subject, REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(REGEXP_REPLACE(subject, '[[:space:]ـً-ٰٟ]', '')), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ى', 'ي'), 'ة', 'ه') AS normalized FROM teachers) old_subjects;
INSERT INTO subjects(name,name_key)
SELECT MIN(u.name), u.name_key FROM subject_upgrade u
LEFT JOIN subjects s ON s.name_key = u.name_key
WHERE s.id IS NULL GROUP BY u.name_key;
ALTER TABLE teachers ADD COLUMN subject_id bigint NULL;
UPDATE teachers t JOIN subject_upgrade u ON u.teacher_id = t.id
JOIN subjects s ON s.name_key = u.name_key SET t.subject_id = s.id;
ALTER TABLE teachers MODIFY COLUMN subject_id bigint NOT NULL;
alter table teachers add constraint fk_teacher_subject foreign key (subject_id) references subjects (id);
ALTER TABLE teachers DROP COLUMN subject;
DROP TEMPORARY TABLE subject_upgrade;
-- القيم الجديدة من المولد، مع الاحتفاظ بجميع أحداث السجل القديمة.
ALTER TABLE audit_logs MODIFY COLUMN action enum ('LOGIN_SUCCEEDED','LOGIN_FAILED','LOGGED_OUT','ACCESS_DENIED','USER_CREATED','USER_UPDATED','USER_DELETED','PAYMENT_RECORDED','EXPENSE_RECORDED','TEACHER_PAYOUT_PAID','STUDENT_CREATED','STUDENT_UPDATED','STUDENT_DELETED','STUDENT_ARCHIVED','STUDENT_RESTORED','STUDENT_ENROLLED','STUDENT_UNENROLLED','GROUP_CREATED','GROUP_UPDATED','GROUP_DELETED','SUBJECT_CREATED','SUBJECT_UPDATED','SUBJECT_DELETED','TEACHER_CREATED','TEACHER_UPDATED','TEACHER_DELETED','SESSION_OPENED','SESSION_CLOSED','SETTINGS_UPDATED','BACKUP_CREATED','BACKUP_RESTORED','ALERT_RULE_UPDATED') NOT NULL;
