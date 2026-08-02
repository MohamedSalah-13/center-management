package com.codejava.center.domain.enums;

/**
 * صلاحيات المستخدمين.
 * enum وليس String: المقارنة النصية كانت تتم بطريقتين مختلفتين في الكود
 * (equalsIgnoreCase في مكان و equals في آخر)، وخطأ مطبعي واحد في قيمة مخزَّنة
 * كان يمنح أو يمنع صلاحيات بصمت.
 */
public enum Role {
    ADMIN("مدير نظام"),
    SECRETARY("سكرتارية");

    private final String arabicName;

    Role(String arabicName) {
        this.arabicName = arabicName;
    }

    public String getArabicName() {
        return arabicName;
    }
}
