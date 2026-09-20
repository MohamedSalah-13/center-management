package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر مصروف واحد في ورقة تقرير المصروفات.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة سلفاً، لا كيان {@code Transaction} نفسه:
 * جاسبر يقرأ الحقول بأسلوب {@code getX()} ولا يعرف قارئات السجلّات - فيخرج العمود فارغاً
 * بلا خطأ - والمبلغ والتاريخ والوقت تنسيقات يملكها {@code MoneyUtils} و{@code I18n} لا
 * تعبيراتٌ داخل ملف تصميم لا يعرف لغة الواجهة ولا عملة السنتر.</p>
 */
@Getter
@RequiredArgsConstructor
public class ExpenseSheetRow {

    private final String serial;
    private final String date;
    private final String time;
    private final String description;
    private final String amount;
}
