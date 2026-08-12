-- يمنع جهازين من تسجيل الطالب في الحصة نفسها معاً.
-- لا تُحذف التكرارات القديمة بصمت؛ إن وجدت يتوقف ALTER لمراجعتها يدوياً.
alter table attendances
    add constraint uk_attendance_student_session unique (student_id, session_id);
