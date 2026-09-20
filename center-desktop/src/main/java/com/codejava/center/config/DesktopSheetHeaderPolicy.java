package com.codejava.center.config;

import com.codejava.center.core.print.SheetHeaderPolicy;
import com.codejava.center.util.PrintPreferences;
import org.springframework.stereotype.Component;

/**
 * تركيب {@link SheetHeaderPolicy} على هذا الطرف: الخانة التي يعلّمها المستخدم في شاشة
 * الإعدادات، محفوظةً لكل جهاز.
 *
 * <p>وجوده يُخرج {@code javafx.print} من طبقة الأعمال: {@code ReportService} كان يستورد
 * {@link PrintPreferences} — وهو يستورد {@code javafx.print.Printer} — فكانت الخدمة تجرّ
 * الواجهة الرسومية خلفها. أما اختيار الطابعة نفسها فلم يعد يمرّ من هنا أصلاً: التسليم
 * كلّه في {@code util/Sheets} الذي يقرأ تفضيلات الجهاز مباشرةً كجاره {@code Printing}،
 * لأن لا حدّ وحدات يفصل بينهما.</p>
 */
@Component
public class DesktopSheetHeaderPolicy implements SheetHeaderPolicy {

    @Override
    public boolean printsCenterHeader() {
        return PrintPreferences.printsCenterHeader();
    }
}
