# فحص ما قبل ترحيلات حواجز التزامن V12–V15

تنطبق هذه الخطوات عند ترقية قاعدة بيانات موجودة من V11 أو أقدم. التركيب الجديد لا
يحتاجها لأن الجداول تبدأ فارغة.

أغلق التطبيق على كل الأجهزة وخذ نسخة احتياطية قابلة للاستعادة، ثم نفّذ الاستعلامات
التالية. النتيجة السليمة لكل استعلام هي **صفر صفوف**:

```sql
select student_id, session_id, count(*) as duplicate_count
from attendances
group by student_id, session_id
having count(*) > 1;

select student_id, group_id, count(*) as duplicate_count
from student_groups
group by student_id, group_id
having count(*) > 1;

select group_id, session_date, count(*) as duplicate_count
from sessions
group by group_id, session_date
having count(*) > 1;

select group_id, count(*) as open_count
from sessions
where is_active = 1
group by group_id
having count(*) > 1;

select student_id, session_id, type, count(*) as duplicate_count
from transactions
where session_id is not null
group by student_id, session_id, type
having count(*) > 1;
```

لا تحذف الصفوف آلياً، وخصوصاً `transactions`: راجع الحضور والخصومات وسجل المراقبة
لتحديد الصف الصحيح، ثم صحّح البيانات بقرار واضح واحتفظ بنسخة ما قبل التصحيح.

## لو بدأ التطبيق وتوقف Flyway بالفعل

بعد إصلاح البيانات، افحص سجل Flyway:

```sql
select installed_rank, version, description, success
from flyway_schema_history
order by installed_rank desc;
```

إن وُجد صف فاشل لإحدى النسخ 12–15، تأكد أولاً أن استعلامات التكرار كلها فارغة وأن
قيد ذلك الإصدار لم يُنشأ، ثم احذف **صف الفشل وحده** وأعد تشغيل التطبيق:

```sql
delete from flyway_schema_history
where success = 0 and version in ('12', '13', '14', '15');
```

لا تحذف أي صف ناجح من `flyway_schema_history`، ولا تعدّل ملف ترحيل طُبق بنجاح.
