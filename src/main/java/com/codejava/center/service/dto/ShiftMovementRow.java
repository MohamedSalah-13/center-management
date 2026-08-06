package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر حركة نقدية واحدة في جرد الوردية.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات، والتنسيق والترجمة
 * قرارات يملكها {@code I18n} و{@code MoneyUtils} لا تعبيراتٌ داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class ShiftMovementRow {

    private final String time;
    private final String amount;
    private final String description;
}
