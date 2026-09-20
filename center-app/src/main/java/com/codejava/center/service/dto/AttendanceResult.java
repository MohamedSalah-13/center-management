package com.codejava.center.service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AttendanceResult {
    private AttendanceOutcome outcome;   // دخول أم انصراف أم رفض - انظر AttendanceOutcome
    private String studentName;         // اسم الطالب
    private String groupName;           // اسم المجموعة الحالية
    private String message;             // رسالة الحالة (مثل: "تم الدخول"، أو "عليه متأخرات")
    private BigDecimal remainingBalance; // الرصيد بعد خصم رسوم الحصة (null في حالة الرفض والانصراف)
    // صف الطالب في كشف اليوم بعد العملية (null في حالة الرفض): تُحدِّث به الشاشة
    // صفّها مباشرة بدل إعادة قراءة كشف اليوم كله بعد كل تمريرة
    private AttendanceLogRow row;

    /** الرفض وحده فشل؛ الدخول والانصراف كلاهما نجاح، وإن اختلف لونهما على الشاشة */
    public boolean isSuccess() {
        return outcome != AttendanceOutcome.REJECTED;
    }
}
