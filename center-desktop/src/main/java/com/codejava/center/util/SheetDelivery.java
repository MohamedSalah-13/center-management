package com.codejava.center.util;

import java.io.File;

/**
 * ما حدث لكشف جاسبر بعد بنائه: خرج من الطابعة، أو صار ملفاً ينتظر أن يُفتح.
 *
 * <p>الاختيار بين الاثنين تفضيل جهاز ({@link PrintPreferences#printsSheetsDirectly()})، وهو
 * قرار واحد لا يصحّ أن يتكرّر في كل شاشة تطبع كشفاً: الشاشة تطلب الورقة من الخدمة وتناولها
 * {@link Sheets}، وهذا الصنف هو ما يقوله المسلِّم عمّا فعل.</p>
 *
 * <p>ومكانه هنا لا في {@code service/dto}: ما يصفه حدثٌ على هذا الجهاز — ورقة خرجت من
 * طابعة اسمها كذا، أو ملفٌ في مجلد مؤقت — ولا معنى له في خدمة تملأ ورقة ولا تعرف أين
 * ستذهب.</p>
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
