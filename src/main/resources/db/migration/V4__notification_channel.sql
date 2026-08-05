-- =====================================================================
-- قناة إشعارات أولياء الأمور: اختيار يملكه السنتر بدل خاصية تُقرأ عند الإقلاع.
--
-- كانت القناة في application.properties (center.notifications.channel)، أي أن
-- تغييرها في نسخة jpackage يعني فتح ملف داخل مجلد البرنامج وإعادة تشغيله. صارت
-- عموداً هنا تعدّله شاشة الإعدادات ويسري على الرسالة التالية.
--
-- الفروق مأخوذة من target/schema-mysql.sql الذي يولّده SchemaScriptGenerator
-- من الكيانات، لا مكتوبة يدوياً.
--
-- كل الأعمدة تقبل NULL عن قصد: قاعدة بيانات قائمة لن تحمل قيمة لأيٍّ منها، وقناة
-- غائبة تعني WHATSAPP_LINK — أي السلوك نفسه قبل الترقية بالضبط.
--
-- ما ليس هنا: مفتاح دخول المزوّد وشكل الرابط. الأول سرّ لا يُحفظ داخل قاعدة تخرج
-- نسخها على فلاشة بجوار أرقام أولياء الأمور نفسها، والثاني يتبع ما هو مثبَّت على
-- الجهاز. كلاهما في NotificationPreferences (تفضيلات الجهاز) بلا ترحيل.
--
-- ملاحظة: ADD COLUMN IF NOT EXISTS لهجة MariaDB وتفشل على MySQL، فلا تُستعمل.
-- =====================================================================

alter table center_settings
    add column notification_channel enum ('WHATSAPP_LINK','WHATSAPP_CLOUD_API','HTTP_GATEWAY'),
    add column notification_api_url varchar(500),
    add column notification_sender_id varchar(100),
    add column notification_template_name varchar(100),
    add column notification_template_language varchar(20),
    add column notification_body_template varchar(500);
