package com.codejava.center.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * صف واحد في شاشة صرف مستحقات المعلمين: حصة مغلقة لم تُصرَف بعد،
 * ومعها المبلغ المحسوب حسب نوع اتفاق المعلم.
 */
public record SessionPayout(
        Long sessionId,
        String groupName,
        Long teacherId,
        String teacherName,
        LocalDate sessionDate,
        long attendees,
        String commissionType,
        BigDecimal totalRevenue,
        BigDecimal payoutAmount
) {
}
