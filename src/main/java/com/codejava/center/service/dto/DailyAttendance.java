package com.codejava.center.service.dto;

import java.time.LocalDate;

/**
 * عدد الحضور في يوم معين (لمخطط الحضور في لوحة القيادة)
 */
public record DailyAttendance(LocalDate date, long count) {
}
