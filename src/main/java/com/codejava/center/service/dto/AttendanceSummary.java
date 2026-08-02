package com.codejava.center.service.dto;

/**
 * سطر واحد في تقرير الحضور والغياب: طالب وعدد الحصص التي حضرها من إجمالي حصص الفترة.
 * عدد الغياب لا يُخزَّن لأنه مشتق: إجمالي الحصص ناقص ما حضره.
 */
public record AttendanceSummary(
        Long studentId,
        String studentName,
        String barcode,
        String parentPhone,
        long attended
) {
}
