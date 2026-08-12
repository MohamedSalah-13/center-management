-- حصة واحدة للمجموعة في اليوم، وحصة مفتوحة واحدة للمجموعة في أي وقت.
-- NULL يتكرر داخل unique، فتظل كل الحصص المغلقة بلا حارس نشط.
alter table sessions
    add constraint uk_session_group_date unique (group_id, session_date),
    add column active_group_guard bigint
        generated always as (case when is_active = 1 then group_id else null end) stored,
    add constraint uk_session_active_group unique (active_group_guard);
