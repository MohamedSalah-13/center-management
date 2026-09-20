package com.codejava.center.config;

import com.codejava.center.core.print.DocumentKind;
import com.codejava.center.core.print.PrintTargetResolver;
import com.codejava.center.util.PrintPreferences;
import org.springframework.stereotype.Component;

/**
 * تركيب {@link PrintTargetResolver} على هذا الطرف: ما اختاره المستخدم في شاشة
 * الإعدادات، محفوظاً لكل جهاز.
 *
 * <p>وجوده يُخرج {@code javafx.print} من طبقة الأعمال: {@code ReportService} كان يستورد
 * {@link PrintPreferences} — وهو يستورد {@code javafx.print.Printer} — فكانت الخدمة
 * تجرّ الواجهة الرسومية كلها خلفها. القيم الثلاث التي تحتاجها نصوصٌ ومنطقٌ فقط، والواجهة
 * تعلنها بلا نوع طباعة واحد.</p>
 */
@Component
public class DesktopPrintTargetResolver implements PrintTargetResolver {

    @Override
    public String printerName(DocumentKind kind) {
        return PrintPreferences.printerName(kind);
    }

    @Override
    public boolean printsSheetsDirectly() {
        return PrintPreferences.printsSheetsDirectly();
    }

    @Override
    public boolean printsCenterHeader() {
        return PrintPreferences.printsCenterHeader();
    }
}
