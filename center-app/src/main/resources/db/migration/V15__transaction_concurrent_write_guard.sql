-- MySQL يسمح بتكرار NULL داخل unique، لذلك تبقى الحركات العامة بلا session قابلة
-- للتسجيل، بينما الحركة المرتبطة بحصة لا تتكرر لنفس الطالب والنوع.
alter table transactions
    add constraint uk_transaction_student_session_type
        unique (student_id, session_id, type);
