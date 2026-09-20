package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر طالب واحد في تقرير الحضور والغياب.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات، والتنسيق والترجمة
 * قرارات يملكها {@code I18n} و{@code MoneyUtils} لا تعبيراتٌ داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class AttendanceReportRow {

    private final String serial;
    private final String studentName;
    private final String barcode;
    private final String parentPhone;
    private final String attended;
    private final String absent;
    private final String rate;
}
