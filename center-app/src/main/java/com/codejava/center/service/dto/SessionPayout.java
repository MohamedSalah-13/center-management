package com.codejava.center.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * صف واحد في شاشة صرف مستحقات المعلمين: حصة مغلقة لم تُصرَف بعد،
 * ومعها المبلغ المحسوب حسب نوع اتفاق المعلم.
 *
 * <p>{@code enrolled} عدد المشتركين في المجموعة يوم انعقاد الحصة، و<b>لا يدخل في
 * حساب المستحق</b>: المستحق يبقى محكوماً باتفاق المعلم (نسبة، مبلغ ثابت، إيجار قاعة)
 * كما كان. وجوده هنا ليُقرأ بجانب عدد الحاضرين - "حضر 12 من 30" يقول عن الحصة ما لا
 * يقوله رقم الحضور وحده، وهو ما يُبنى عليه قرار استمرار المجموعة أو مراجعة الاتفاق.</p>
 */
public record SessionPayout(
        Long sessionId,
        String groupName,
        Long teacherId,
        String teacherName,
        LocalDate sessionDate,
        long attendees,
        long enrolled,
        String commissionType,
        BigDecimal totalRevenue,
        BigDecimal payoutAmount
) {

    /** نسبة الحاضرين من المشتركين، أو {@code null} حين لا مشترك مسجَّل في ذلك اليوم */
    public Integer attendanceRate() {
        return enrolled == 0 ? null : (int) Math.round((attendees * 100.0) / enrolled);
    }
}
