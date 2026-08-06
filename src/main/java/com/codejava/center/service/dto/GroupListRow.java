package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في كشف المجموعات المطبوع.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة، لنفس سببَي {@link GroupRosterRow}: جاسبر
 * يطلب {@code getTeacherName()} ولا يعرف قارئات السجلّات، والأيام والمواعيد والمبالغ
 * والصفوف تُصاغ بـ {@code WeekDays} و{@code MoneyUtils} و{@code I18n} - لا بتعبيرات داخل
 * ملف تصميم لا يعرف لغة الواجهة ولا عملة السنتر.</p>
 */
@Getter
@RequiredArgsConstructor
public class GroupListRow {

    private final String name;
    private final String teacherName;
    private final String level;
    private final String days;
    private final String time;
    private final String members;
    private final String price;
}
