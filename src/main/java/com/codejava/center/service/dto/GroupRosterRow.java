package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في كشف المجموعة المطبوع.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة، لنفس سببَي {@link EnrollmentReportRow}:
 * جاسبر يطلب {@code getStudentName()} ولا يعرف قارئات السجلّات، و"---" و"٪" قرارات لغة
 * يملكها {@code I18n} لا تعبيراتٌ داخل ملف تصميم لا تراه حزم النصوص.</p>
 *
 * <p>{@code serial} رقم السطر في الورقة لا معرّف الطالب: الكشف يُقرأ ويُؤشَّر عليه بالقلم
 * أثناء نداء الأسماء، ورقم قاعدة البيانات لا يفيد في ذلك بشيء.</p>
 */
@Getter
@RequiredArgsConstructor
public class GroupRosterRow {

    private final String serial;
    private final String studentName;
    private final String barcode;
    private final String parentPhone;
    private final String joinDate;
    private final String attendance;
    private final String attendanceRate;
}
