package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في كشف المعلمين المطبوع.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة، لنفس سببَي {@link GroupListRow}: جاسبر يطلب
 * {@code getCommissionType()} ولا يعرف قارئات السجلّات، ونوع العمولة يصل مترجَماً بـ
 * {@code CommissionTypes} لا رمزاً إنجليزياً مخزَّناً - وملف التصميم لا يعرف لغة الواجهة.</p>
 *
 * <p>وقيمة العمولة تُصاغ بـ {@code MoneyUtils.format} بلا رمز عملة: هي نسبة مئوية حين يكون
 * الاتفاق نسبةً، ومبلغٌ حين يكون ثابتاً أو إيجاراً، وإلحاق العملة بها يجعل "50" تُقرأ
 * خمسين جنيهاً وهي خمسون في المئة.</p>
 */
@Getter
@RequiredArgsConstructor
public class TeacherListRow {

    private final String serial;
    private final String name;
    private final String subject;
    private final String commissionType;
    private final String commissionValue;
}
