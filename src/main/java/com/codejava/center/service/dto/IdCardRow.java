package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * كارنيه طالب واحد كما يُطبع.
 *
 * <p>صنف بقارئات JavaBean، لنفس سبب {@link EnrollmentReportRow}: جاسبر يقرأ
 * {@code getBarcode()} ولا يعرف قارئات السجلّات.</p>
 *
 * <p>وهو يقف بين الكيان وملف التصميم بدل تمرير {@code Student} إليه مباشرةً، لأن
 * {@code schoolLevel} في الكيان قيمة {@code enum} بينما التصميم يعلن الحقل نصاً - فكان
 * الكارنيه يفشل عند الملء - ولأن الاسم المعروض للمرحلة ترجمةٌ يملكها {@code I18n}
 * لا {@code toString()} على ثابت.</p>
 */
@Getter
@RequiredArgsConstructor
public class IdCardRow {

    private final String name;
    private final String barcode;
    private final String level;
}
