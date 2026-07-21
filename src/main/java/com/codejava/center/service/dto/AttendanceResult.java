package com.codejava.center.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttendanceResult {
    private boolean isSuccess;     // هل الدخول مسموح أم مرفوض؟
    private String studentName;    // اسم الطالب
    private String groupName;      // اسم المجموعة الحالية
    private String message;        // رسالة الحالة (مثل: "تم الدخول"، أو "عليه متأخرات")
}