package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر حصة واحدة في كشف حساب المعلم.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات، والتنسيق والترجمة
 * قرارات يملكها {@code I18n} و{@code MoneyUtils} لا تعبيراتٌ داخل ملف تصميم.</p>
 */
@Getter
@RequiredArgsConstructor
public class TeacherSessionRow {

    private final String sessionDate;
    private final String groupName;
    private final String attendance;
    private final String revenue;
    private final String payout;
}
