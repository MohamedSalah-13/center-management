package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * الصف الدراسي للطالب وللمجموعة.
 *
 * <p>كانت المرحلة تُحفظ نصاً مترجَماً كما كتبته الشاشة ("الصف الأول الإعدادي" أو
 * "Preparatory Year 1")، فطالب سُجّل بواجهة إنجليزية لا يطابق مجموعة أُنشئت بالعربية.
 * ولمّا صار الصف قيداً يمنع الاشتراك، صار لا بد أن يكون <b>قيمة ثابتة تُخزَّن باسمها
 * ويُترجَم اسمها عند العرض</b> - نفس قاعدة سجل المراقبة والتنبيهات.</p>
 *
 * <p>الترتيب هو ترتيب التعليم لا الأبجدية: قوائم الاختيار تعرضه كما هو.</p>
 */
public enum SchoolLevel {

    PREP1,
    PREP2,
    PREP3,
    SEC1,
    SEC2,
    SEC3;

    public String getDisplayName() {
        return I18n.get("schoolLevel." + name());
    }
}
