package com.codejava.center.service.dto;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * صفٌّ واحد في كشف الحضور والانصراف: طالبٌ في حصة، ومتى دخل ومتى خرج.
 *
 * <p>إسقاطٌ لا كيان: الصفوف تعبر حدّ الـ Transaction إلى الواجهة، و{@code Attendance}
 * يجرّ خلفه {@code student} و{@code session} الكسولين فينفجر أول عمود يُقرأ منهما.</p>
 *
 * <p>{@code sessionActive} جزء من الصفّ لا زينة: هو ما يفرّق بين "بالداخل الآن" و"لم
 * يُسجَّل انصرافه" - وكلاهما {@code timeOut} فارغة.</p>
 */
public record AttendanceLogRow(
        Long attendanceId,
        String studentName,
        String barcode,
        String parentPhone,
        String groupName,
        LocalDate sessionDate,
        LocalDateTime timeIn,
        LocalDateTime timeOut,
        boolean sessionActive) {

    public AttendanceState state() {
        if (timeOut != null) return AttendanceState.LEFT;
        return sessionActive ? AttendanceState.INSIDE : AttendanceState.NOT_RECORDED;
    }

    /**
     * مدة المكوث، أو {@code null} إن كانت غير معلومة.
     *
     * <p>لمن بالداخل تُقاس إلى اللحظة الحالية، فهي تكبر ما دام في القاعة. ولمن أُغلقت
     * حصته بلا تسجيل انصراف تبقى {@code null}: الفرق بين وقت دخوله ووقت إغلاق الحصة
     * رقمٌ يبدو قياساً وهو تخمين.</p>
     */
    public Duration duration() {
        return switch (state()) {
            case LEFT -> Duration.between(timeIn, timeOut);
            case INSIDE -> Duration.between(timeIn, LocalDateTime.now());
            case NOT_RECORDED -> null;
        };
    }
}
