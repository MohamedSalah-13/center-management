package com.codejava.center.service.alert;

import com.codejava.center.domain.Alert;

import java.util.List;

/**
 * ما تسلّمه الشاشة من {@link AlertFeed} في كل نبضة.
 *
 * <p>القائمة والعدّاد معاً في تسليمة واحدة عن قصد: هما نتيجتا استعلامين وقعا في نفس
 * اللحظة، وتسليمهما منفصلين يترك الشاشة تعرض بطاقة تنبيه جديد بينما العدّاد بجوارها
 * ما زال على رقم الدقيقة الماضية.</p>
 *
 * @param fresh     ما استُجدّ منذ التسليمة السابقة ولم يُعرض بعد؛ قد تكون فارغة
 * @param openCount إجمالي ما لم يُعالَج الآن، بما فيه ما عولج أو أُطلق على جهاز آخر
 */
public record AlertBatch(List<Alert> fresh, long openCount) {

    public boolean hasFresh() {
        return !fresh.isEmpty();
    }
}
