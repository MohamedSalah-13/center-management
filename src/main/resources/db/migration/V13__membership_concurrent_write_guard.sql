-- صف عضوية واحد لكل طالب ومجموعة؛ الانسحاب والعودة يغيران الصف نفسه.
-- قُسّم كل جدول في ترحيل مستقل لأن DDL في MySQL لا يتراجع كمعاملة عادية.
alter table student_groups
    add constraint uk_membership_student_group unique (student_id, group_id);
