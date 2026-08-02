package com.codejava.center.service.dto;

import java.util.List;

/**
 * تقرير حضور وغياب مجموعة خلال فترة.
 * إجمالي الحصص يُحمل مع القائمة لأن الغياب لا معنى له بدونه:
 * طالب حضر 3 حصص إمّا ممتاز أو سيّئ حسب عدد الحصص المنعقدة.
 */
public record GroupAttendanceReport(
        String groupName,
        long totalSessions,
        List<AttendanceSummary> rows
) {
}
