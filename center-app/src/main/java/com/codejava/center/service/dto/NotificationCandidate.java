package com.codejava.center.service.dto;

import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.util.I18n;

/**
 * مرشّح لإرسال إشعار: الطالب ورقم وليّه ونص الرسالة الجاهز.
 *
 * <p>{@code phoneValid} و {@code alreadyNotified} يُحسبان مسبقاً لتعرضهما الشاشة
 * قبل الإرسال: الرقم غير الصالح والإشعار المكرر يجب أن يظهرا للمستخدم بوضوح
 * بدل أن يفشل الإرسال بصمت أو يزعج ولي الأمر برسالة مكررة.</p>
 */
public record NotificationCandidate(
        Long studentId,
        String studentName,
        AlertType type,
        String rawPhone,
        String internationalPhone,
        String message,
        boolean phoneValid,
        boolean alreadyNotified
) {
    /** جاهز للإرسال: الرقم صالح ولم يُرسل له إشعار مماثل خلال الفترة */
    public boolean sendable() {
        return phoneValid && !alreadyNotified;
    }

    public String statusLabel() {
        if (!phoneValid) return I18n.get("notify.status.invalidPhone");
        if (alreadyNotified) return I18n.get("notify.status.alreadySent");
        return I18n.get("notify.status.ready");
    }
}
