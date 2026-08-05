-- =====================================================================
-- قيود المجموعة: الصف الدراسي، موعد الأسبوع، وتاريخ الخروج من المجموعة
--
-- الفروق مأخوذة من target/schema-mysql.sql المولَّد من الكيانات، لا مكتوبة
-- يدوياً: ddl-auto=validate يرفض بدء التشغيل عند أي اختلاف.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) المرحلة الدراسية: من نص مترجَم إلى رمز ثابت
--
-- كانت الشاشة تحفظ النص كما ظهر فيها ("الصف الأول الإعدادي" أو "Preparatory
-- Year 1")، فالقيمة نفسها تختلف باختلاف لغة الجهاز الذي سُجّل عليه الطالب.
-- ولمّا صار الصف قيداً يمنع اشتراك طالب في مجموعة صف آخر، لم يعد يصح أن يكون
-- المخزَّن نصاً معروضاً. الترتيب مقصود: تحويل القيم أولاً ثم تغيير نوع العمود،
-- لأن العمود بنوعه الجديد لا يقبل إلا هذه الرموز.
-- ---------------------------------------------------------------------
UPDATE students SET school_level = 'PREP1'
 WHERE school_level IN ('الصف الأول الإعدادي', 'Preparatory Year 1');
UPDATE students SET school_level = 'PREP2'
 WHERE school_level IN ('الصف الثاني الإعدادي', 'Preparatory Year 2');
UPDATE students SET school_level = 'PREP3'
 WHERE school_level IN ('الصف الثالث الإعدادي', 'Preparatory Year 3');
UPDATE students SET school_level = 'SEC1'
 WHERE school_level IN ('الصف الأول الثانوي', 'Secondary Year 1');
UPDATE students SET school_level = 'SEC2'
 WHERE school_level IN ('الصف الثاني الثانوي', 'Secondary Year 2');
UPDATE students SET school_level = 'SEC3'
 WHERE school_level IN ('الصف الثالث الثانوي', 'Secondary Year 3');

-- ما لا يُعرف (نص كُتب يدوياً أو مرحلة أُلغيت) يصير فارغاً لا يُترك كما هو:
-- تركه يُسقط تغيير نوع العمود على قاعدة العميل، وتخمين مرحلته يضع طالباً في
-- صفٍّ ليس صفَّه. الحقل الفارغ يظهر في الشاشة ويُستكمل بضغطة.
UPDATE students SET school_level = NULL
 WHERE school_level IS NOT NULL
   AND school_level NOT IN ('PREP1', 'PREP2', 'PREP3', 'SEC1', 'SEC2', 'SEC3');

ALTER TABLE students
    MODIFY COLUMN school_level enum ('PREP1','PREP2','PREP3','SEC1','SEC2','SEC3');

-- ---------------------------------------------------------------------
-- 2) صف المجموعة وموعدها الأسبوعي
--
-- الأعمدة تقبل NULL عمداً: المجموعات القائمة قبل هذه الميزة بلا صف ولا موعد،
-- وجعلها NOT NULL يعني إما رفض الترحيل عند العميل أو اختراع موعد لها. الشرط
-- مفروض في CourseGroupService.saveGroup: أول تعديل على مجموعة قديمة يستكمل
-- بياناتها، والاشتراك فيها مرفوض حتى يُضبط صفها.
-- ---------------------------------------------------------------------
ALTER TABLE course_groups
    ADD COLUMN school_level enum ('PREP1','PREP2','PREP3','SEC1','SEC2','SEC3'),
    ADD COLUMN meeting_days varchar(100),
    ADD COLUMN start_time time(6),
    ADD COLUMN end_time time(6),
    ADD COLUMN auto_name bit not null default b'1';

-- ---------------------------------------------------------------------
-- 3) تاريخ الخروج من المجموعة
--
-- بدونه لا جواب لسؤال "كم حصة حضرها الطالب": حصص المجموعة تُعدّ من يوم إنشائها
-- لا من يوم التحاق الطالب بها، فيظهر الملتحق حديثاً غائباً عن حصص عُقدت قبل أن
-- يعرف السنتر. NULL تعني اشتراكاً سارياً.
-- ---------------------------------------------------------------------
ALTER TABLE student_groups
    ADD COLUMN leave_date date;
