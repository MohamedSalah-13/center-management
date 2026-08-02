package com.codejava.center.domain.enums;

public enum TransactionType {
    INCOME,         // إيراد (دفع الطالب في الخزينة - نقد داخل للخزينة)
    SESSION_CHARGE, // خصم رسوم حصة من رصيد الطالب عند تسجيل الحضور (استحقاق وليس حركة نقدية)
    EXPENSE,        // مصروفات (نثريات، كهرباء، صيانة)
    TEACHER_PAYOUT  // سداد مستحقات المعلم
}
