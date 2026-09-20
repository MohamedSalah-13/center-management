package com.codejava.center.service.dto;

import java.math.BigDecimal;

/**
 * رصيد طالب مع بيانات التواصل، لتقرير المتأخرات.
 * يتضمّن هاتف ولي الأمر لأن التقرير هو المدخل الطبيعي لإشعار الأهالي لاحقاً.
 */
public record StudentBalance(
        Long studentId,
        String studentName,
        String barcode,
        String phone,
        String parentPhone,
        BigDecimal balance
) {
    /** المبلغ المستحق على الطالب (الرصيد السالب بقيمة موجبة) */
    public BigDecimal amountDue() {
        return balance.signum() < 0 ? balance.negate() : BigDecimal.ZERO;
    }
}
