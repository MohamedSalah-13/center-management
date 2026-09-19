package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر حدث واحد في سجل المراقبة المطبوع.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات، والتنسيق والترجمة
 * قرارات يملكها {@code I18n} و{@code MoneyUtils} لا تعبيراتٌ داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class AuditReportRow {

    private final String occurredAt;
    private final String actor;
    private final String action;
    private final String target;
    private final String amount;
    private final String status;
    private final String details;
}
