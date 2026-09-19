package com.codejava.center.service.dto;

import java.io.File;

/**
 * ما حدث لكشف جاسبر بعد بنائه: خرج من الطابعة، أو صار ملفاً ينتظر أن يُفتح.
 *
 * <p>الاختيار بين الاثنين تفضيل جهاز ({@code PrintPreferences.printsSheetsDirectly})، وهو
 * قرار واحد لا يصحّ أن يتكرّر في كل شاشة تطبع كشفاً: الشاشة تطلب الكشف، والخدمة تسلّمه،
 * وهذا الصنف هو ما تقوله الخدمة عمّا فعلت.</p>
 *
 * @param pdf         الملف الناتج، أو {@code null} إن ذهب الكشف إلى الطابعة رأساً
 * @param printerName اسم الطابعة، أو {@code null} إن لم يُطبع
 */
public record SheetDelivery(File pdf, String printerName) {

    public static SheetDelivery printed(String printerName) {
        return new SheetDelivery(null, printerName);
    }

    public static SheetDelivery exported(File pdf) {
        return new SheetDelivery(pdf, null);
    }

    public boolean wasPrinted() {
        return printerName != null;
    }
}
