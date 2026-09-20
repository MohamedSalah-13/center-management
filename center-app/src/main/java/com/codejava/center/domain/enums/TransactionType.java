package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

public enum TransactionType {
    INCOME,         // إيراد (دفع الطالب في الخزينة - نقد داخل للخزينة)
    SESSION_CHARGE, // خصم رسوم حصة من رصيد الطالب عند تسجيل الحضور (استحقاق وليس حركة نقدية)
    EXPENSE,        // مصروفات (نثريات، كهرباء، صيانة)
    TEACHER_PAYOUT; // سداد مستحقات المعلم

    /**
     * الاسم المعروض بلغة الواجهة الحالية.
     * كانت شاشة تقفيل الوردية تحمل {@code switch} خاصاً بها لتسمية الأنواع بالعربية،
     * فأي نوع جديد يوجب تعديل الشاشة بدل تعديل الـ enum وحده.
     */
    public String getDisplayName() {
        return I18n.get("transactionType." + name());
    }
}
