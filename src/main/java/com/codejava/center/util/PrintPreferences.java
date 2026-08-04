package com.codejava.center.util;

import javafx.print.Printer;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * إعدادات الطباعة: الطابعة المختارة وأسلوب الطباعة.
 *
 * <p><b>تُحفظ لكل جهاز</b> عبر {@link Preferences} لا في {@code CenterSettings}، لنفس سبب
 * {@link I18n}: الطابعات مُثبَّتة على الجهاز لا على السنتر. جهاز البوابة قد يكون موصولاً
 * بطابعة حرارية صغيرة للإيصالات، وجهاز الإدارة بطابعة A4 للتقارير، واسم الطابعة نفسه
 * لا معنى له على جهاز آخر. لهذا لا يوجد ملف ترحيل Flyway مقابل لهذه الميزة.</p>
 *
 * <p>ساكن لا bean من Spring لأن {@code ReportService} و{@code Printing} يستدعيانه من مواضع
 * لا حقن فيها، تماماً كـ {@link MoneyUtils} و{@link I18n}.</p>
 */
public final class PrintPreferences {

    /** ما يحدث عند الضغط على "طباعة" في أي شاشة */
    public enum PrintMode {
        /** عرض المستند في نافذة معاينة، والطباعة بضغطة ثانية */
        PREVIEW,
        /** فتح نافذة الطابعة الخاصة بنظام التشغيل (السلوك التاريخي) */
        DIALOG,
        /** الإرسال فوراً إلى الطابعة المختارة بلا أي نافذة */
        DIRECT;

        public String getDisplayName() {
            return I18n.get("printMode." + name());
        }
    }

    private static final String PRINTER_KEY = "print.printerName";
    private static final String MODE_KEY = "print.mode";

    /** الافتراضي هو سلوك النسخ السابقة من البرنامج: نافذة الطابعة عند كل طباعة */
    private static final PrintMode DEFAULT_MODE = PrintMode.DIALOG;

    private PrintPreferences() {
    }

    /** اسم الطابعة المختارة، أو {@code null} أي "اترك الأمر لطابعة النظام الافتراضية" */
    public static String printerName() {
        try {
            String saved = prefs().get(PRINTER_KEY, null);
            return saved == null || saved.isBlank() ? null : saved;
        } catch (SecurityException e) {
            // بيئات ويندوز المقيَّدة تمنع قراءة سجل المستخدم؛ طابعة النظام الافتراضية تكفي
            return null;
        }
    }

    /** @param name اسم الطابعة، أو {@code null} للعودة إلى طابعة النظام الافتراضية */
    public static void setPrinterName(String name) {
        try {
            Preferences prefs = prefs();
            if (name == null || name.isBlank()) {
                prefs.remove(PRINTER_KEY);
            } else {
                prefs.put(PRINTER_KEY, name);
            }
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // فشل الحفظ يعني عودة الاختيار للافتراضي بعد إعادة التشغيل فقط، لا يستحق إسقاط العملية
        }
    }

    public static PrintMode mode() {
        try {
            String saved = prefs().get(MODE_KEY, null);
            return saved == null ? DEFAULT_MODE : PrintMode.valueOf(saved);
        } catch (SecurityException | IllegalArgumentException e) {
            // IllegalArgumentException: قيمة محفوظة بنسخة أقدم لم تعد ضمن الـ enum
            return DEFAULT_MODE;
        }
    }

    public static void setMode(PrintMode mode) {
        try {
            Preferences prefs = prefs();
            prefs.put(MODE_KEY, mode.name());
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // كما في setPrinterName: الأسلوب يعود للافتراضي بعد إعادة التشغيل فقط
        }
    }

    /**
     * الطابعة التي ستُستعمل فعلياً.
     *
     * <p>الطابعة المحفوظة قد تكون فُصلت أو أُعيدت تسميتها بعد اختيارها، ولا يصح أن يعني ذلك
     * أن الإيصالات تتوقف: نعود إلى طابعة النظام الافتراضية بدل رمي استثناء. الشاشة تنبّه
     * إلى الغياب عبر {@link #savedPrinterIsMissing()}.</p>
     *
     * @return الطابعة، أو {@code null} إن لم يكن على الجهاز أي طابعة أصلاً
     */
    public static Printer resolvePrinter() {
        String saved = printerName();
        if (saved != null) {
            for (Printer printer : Printer.getAllPrinters()) {
                if (printer.getName().equals(saved)) {
                    return printer;
                }
            }
        }
        return Printer.getDefaultPrinter();
    }

    /** هل اختار المستخدم طابعة لم تعد موجودة على الجهاز */
    public static boolean savedPrinterIsMissing() {
        String saved = printerName();
        if (saved == null) {
            return false;
        }
        return Printer.getAllPrinters().stream().noneMatch(printer -> printer.getName().equals(saved));
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(PrintPreferences.class);
    }
}
