package com.codejava.center.service.dto;

import com.codejava.center.domain.AuditLog;

import java.util.List;

/**
 * صفحة من سجل المراقبة: الأسطر المعروضة وعددها الكلي.
 *
 * <p>العدد الكلي يُجلب مع الأسطر لا بعدها: السجل ينمو بلا حدّ والشاشة تقصّ النتائج عند
 * سقف ثابت، فلولا هذا الرقم لظهرت ألف حدث وكأنها كل ما وقع في الفترة - وهو أخطر ما
 * يمكن أن يقوله سجل مراقبة.</p>
 */
public record AuditPage(List<AuditLog> rows, long totalMatching) {

    /** هل بقي خارج الشاشة ما يطابق التصفية؟ */
    public boolean isTruncated() {
        return totalMatching > rows.size();
    }
}
