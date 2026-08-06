package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر مدين واحد في تقرير المتأخرات.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات، والتنسيق والترجمة
 * قرارات يملكها {@code I18n} و{@code MoneyUtils} لا تعبيراتٌ داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class ArrearsReportRow {

    private final String serial;
    private final String studentName;
    private final String barcode;
    private final String parentPhone;
    private final String amountDue;
}
