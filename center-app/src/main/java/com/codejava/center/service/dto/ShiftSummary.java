package com.codejava.center.service.dto;

import java.math.BigDecimal;

/**
 * جرد الوردية: تفصيل الحركة النقدية في فترة بدلاً من رقم صافٍ واحد.
 * لا تدخل فيه حركات SESSION_CHARGE لأنها استحقاق على الطالب وليست نقداً في الدرج.
 */
public record ShiftSummary(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal totalTeacherPayouts,
        BigDecimal net
) {
}
