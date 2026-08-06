package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في ورقة كشف الحضور والانصراف.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لا {@link AttendanceLogRow} نفسه:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات - فيخرج العمود فارغاً
 * بلا خطأ - والتنسيق والترجمة قرارات يملكها {@code I18n} و{@code Durations} لا تعبيراتٌ
 * داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class AttendanceLogSheetRow {

    private final String serial;
    private final String studentName;
    private final String groupName;
    private final String date;
    private final String timeIn;
    private final String timeOut;
    private final String duration;
    private final String state;
}
