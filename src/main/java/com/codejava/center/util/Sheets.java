package com.codejava.center.util;

import com.codejava.center.service.dto.SheetDelivery;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * ما تراه الشاشة بعد أن يُسلَّم كشف جاسبر.
 *
 * <p>سطرٌ واحد لكل شاشة تطبع كشفاً: "أُرسل إلى الطابعة فلانة"، أو فتح الـ PDF. كُتب هنا لا
 * في المتحكّمات لأن الشاشة الثانية التي طبعت كشفاً نسخت الأولى، والثالثة كانت ستنسخ
 * الثانية - ومعها احتمال أن تنسى إحداها أن تقول شيئاً حين يفشل الفتح.</p>
 *
 * <p>يُستدعى على خيط الواجهة: {@link Dialogs} ينشئ نوافذ JavaFX.</p>
 */
public final class Sheets {

    private Sheets() {
    }

    public static void show(SheetDelivery delivery) {
        if (delivery.wasPrinted()) {
            Dialogs.success(I18n.format("report.sheet.sentTo", delivery.printerName()));
            return;
        }
        open(delivery.pdf());
    }

    /**
     * فتح الملف بعارض الـ PDF المثبَّت على الجهاز.
     * تعذُّر الفتح لا يعني فشل الكشف، فيُقال أين هو بدل أن تُبتلع العملية كلها.
     */
    private static void open(File pdf) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdf);
                return;
            }
        } catch (IOException e) {
            // يقع حين لا يكون على الجهاز برنامج مرتبط بامتداد pdf
        }
        Dialogs.info(I18n.format("report.sheet.openFailed", pdf.getAbsolutePath()));
    }
}
