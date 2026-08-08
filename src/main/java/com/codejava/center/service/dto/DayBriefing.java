package com.codejava.center.service.dto;

import java.time.LocalTime;

/**
 * موجز يوم واحد في أربعة أرقام واسمٍ واحد: ما يُقال لمن فتح البرنامج توّاً.
 *
 * <p>غير {@link DayScheduleRow}: ذاك سطرٌ لكل مجموعة يُقرأ في جدول، وهذا الجدولُ كلُّه
 * في سطرين يُقرآن في بطاقة تختفي بعد سبع ثوانٍ. لو حملت البطاقة الصفوف لَاحتاجت أن
 * تُختصر عند العرض، وهو اختصارٌ يُكتب مرة في الشاشة ومرة في البطاقة فيختلفان.</p>
 *
 * <p>{@code nextGroupName} و{@code nextStartTime} فارغان معاً حين لا موعد باقياً اليوم -
 * إما لأن كل المجموعات فُتحت، أو لأن الوقت تجاوز آخر موعد. والفارغ هنا خبرٌ لا نقص:
 * البطاقة تسقط السطر الثاني بدل أن تخترع موعداً.</p>
 *
 * <p>سجلّ لا صنفاً بقارئات JavaBean: هذا لا يذهب إلى جاسبر - لا ورقة تُطبع منه - فلا
 * يلزمه شكل {@code getX()} الذي تفرضه ملفات التصميم.</p>
 *
 * @param total         كم مجموعة يخصّها هذا اليوم: موعدها فيه أو فُتحت لها حصة فيه
 * @param open          حصص جارية الآن
 * @param notOpened     مجموعات لم تُفتح لها حصة بعد
 * @param closed        حصص انتهت
 * @param nextGroupName اسم أقرب مجموعة لم يحن دورها بعد، أو {@code null}
 * @param nextStartTime موعد تلك المجموعة، أو {@code null}
 */
public record DayBriefing(int total, long open, long notOpened, long closed,
                          String nextGroupName, LocalTime nextStartTime) {

    /** لا مجموعة يجتمع موعدها اليوم ولا حصة فُتحت فيه */
    public boolean isEmpty() {
        return total == 0;
    }

    /** هل بقي موعد لم يحن بعد اليوم؟ */
    public boolean hasNext() {
        return nextGroupName != null;
    }
}
