# مصدر المرحلة السادسة في خارطة الطريق

`Center_System_Roadmap.pdf` في جذر المستودع **ملفٌّ ثنائي بلا مصدر**: صفحاتُه الأربع
الأولى وصلت جاهزة، ولا HTML لها في المستودع. فالمرحلة السادسة **أُلحقت** بها ولم
تُعَد كتابةُ الملف كلِّه - وذلك مقصود: إعادةُ بنائه من نصٍّ مستخرَج من PDF تُفقد ما لا
يُستخرج، والصفحاتُ الأربع تبقى كما هي حرفاً بحرف.

`saas-phase.html` هو مصدر الصفحتين المضافتين. صُنع الملفُّ الأصلي بـ WeasyPrint
(يقولها حقلُ `Producer` فيه)، وخطوطُه DejaVu Sans وLiberation Sans وNoto Color Emoji -
وهي خطوطُ لينكس الافتراضية، فلا شيء يُثبَّت ليتطابق الشكل.

لتحديث المرحلة السادسة:

```bash
pip install weasyprint pymupdf
python3 - <<'PY'
from weasyprint import HTML
import pymupdf

HTML('docs/roadmap/saas-phase.html').write_pdf('/tmp/saas.pdf')

# الصفحات الأربع الأولى من نسخةٍ سبقت الإلحاق - أو احذف المضافتين من الحالية
book = pymupdf.open('Center_System_Roadmap.pdf')
book.delete_pages(4, book.page_count - 1)
book.insert_pdf(pymupdf.open('/tmp/saas.pdf'))
book.save('Center_System_Roadmap.pdf', garbage=0, deflate=True)
PY
```

وفهرسُ العناوين (bookmarks) متداخل: عنوانُ الوثيقة في المستوى 1، والمراحل في 2،
والبنود في 3. مرحلةٌ تُضاف في المستوى 1 تصير أختاً للعنوان لا للمراحل.
