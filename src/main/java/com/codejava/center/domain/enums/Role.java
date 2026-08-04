package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

/**
 * صلاحيات المستخدمين.
 * enum وليس String: المقارنة النصية كانت تتم بطريقتين مختلفتين في الكود
 * (equalsIgnoreCase في مكان و equals في آخر)، وخطأ مطبعي واحد في قيمة مخزَّنة
 * كان يمنح أو يمنع صلاحيات بصمت.
 */
public enum Role {
    ADMIN,
    SECRETARY;

    /**
     * الاسم المعروض بلغة الواجهة الحالية.
     * حلّ محل {@code getArabicName()} الذي كان يحمل النص العربي داخل الـ enum نفسه،
     * فلا يمكن ترجمته؛ المفتاح الآن {@code role.ADMIN} في حزمة النصوص.
     */
    public String getDisplayName() {
        return I18n.get("role." + name());
    }
}
