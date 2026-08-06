package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في كشف اشتراكات الطالب المطبوع.
 *
 * <p>صنف بقارئات JavaBean لا {@code record} مثل {@link MembershipRow}: جاسبر يقرأ الحقول
 * بأسلوب {@code getGroupName()}، وسجلّات جافا تُسمّي قارئاتها {@code groupName()} فلا
 * يجدها ويخرج الكشف بأعمدة فارغة بلا خطأ يُعلن.</p>
 *
 * <p>وقيمه نصوص مُنسَّقة سلفاً لا تواريخ وأرقاماً: "مستمر" و"---" و"٪" قرارات لغةٍ
 * يملكها {@code I18n}، وكتابتها تعبيراتٍ داخل ملف jrxml تضعها في مكان ثانٍ لا يعرف
 * لغة الواجهة ولا يفحصه {@code MessageBundleTest}.</p>
 */
@Getter
@RequiredArgsConstructor
public class EnrollmentReportRow {

    private final String groupName;
    private final String joinDate;
    private final String leaveDate;
    private final String sessionsHeld;
    private final String sessionsAttended;
    private final String attendanceRate;
}
