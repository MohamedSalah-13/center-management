package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * تصنيف أحداث سجل المراقبة، لتصفية الشاشة.
 *
 * <p>ليس عموداً في قاعدة البيانات: التصنيف صفة ثابتة لنوع الحدث نفسه
 * ({@link AuditAction}) ولا يتغيّر بمرور الوقت، فتخزينه يعني مصدرين للحقيقة
 * قد يختلفان. الاستعلام يصفّي بـ {@code action IN (...)} بدلاً من ذلك.</p>
 */
public enum AuditCategory {

    /** الدخول والخروج ومحاولات الوصول المرفوضة وإدارة المستخدمين */
    SECURITY,

    /** كل ما يمسّ النقود: تحصيل، مصروفات، صرف مستحقات */
    FINANCE,

    /** إنشاء وتعديل وحذف بيانات الطلاب والمجموعات والمعلمين والحصص */
    DATA,

    /** الإعدادات والنسخ الاحتياطي والاستعادة */
    SYSTEM;

    public String getDisplayName() {
        return I18n.get("auditCategory." + name());
    }
}
