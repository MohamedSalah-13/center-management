package com.codejava.center.util;

import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * إعدادات الطباعة: طابعة ومقاس ورق لكل نوع مستند، وأسلوب الطباعة.
 *
 * <p><b>تُحفظ لكل جهاز</b> عبر {@link Preferences} لا في {@code CenterSettings}، لنفس سبب
 * {@link I18n}: الطابعات مُثبَّتة على الجهاز لا على السنتر، واسم الطابعة لا معنى له على
 * جهاز آخر. لهذا لا يوجد ملف ترحيل Flyway مقابل لهذه الميزة.</p>
 *
 * <p>الإعداد مفصول حسب {@link DocumentKind}: جهاز الإدارة قد يطبع التقارير على A4 والإيصالات
 * على طابعة حرارية موصولة بنفس الجهاز، وهما مقاسان لا يجمعهما إعداد واحد. أما أسلوب الطباعة
 * ({@link PrintMode}) فواحد للبرنامج كله لأنه تفضيل تشغيل لا خاصية ورق.</p>
 *
 * <p>مقاسات الورق تُقرأ من الطابعة المختارة نفسها ({@code getSupportedPapers}) لا من قائمة
 * مكتوبة في الكود: الطابعة الحرارية تُعلن مقاس الرول الحقيقي (80mm) الذي لا يمكن تخمينه،
 * وقائمة ثابتة قد تعرض مقاساً ترفضه الطابعة فتفشل الطباعة عند المستخدم.</p>
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

    private static final String MODE_KEY = "print.mode";
    private static final String DIRECT_SHEETS_KEY = "print.sheetsDirect";
    private static final String CENTER_HEADER_KEY = "print.centerHeader";

    /**
     * مفتاح الطابعة قبل فصل الإعداد حسب نوع المستند. يُقرأ كقيمة احتياطية للنوعين معاً
     * حتى لا يفقد جهازٌ مضبوطٌ سلفاً اختياره لمجرد التحديث.
     */
    private static final String LEGACY_PRINTER_KEY = "print.printerName";

    /** الافتراضي هو سلوك النسخ السابقة من البرنامج: نافذة الطابعة عند كل طباعة */
    private static final PrintMode DEFAULT_MODE = PrintMode.DIALOG;

    private PrintPreferences() {
    }

    // ---------------------------------------------------------------- الطابعة

    /** اسم الطابعة المختارة لهذا النوع، أو {@code null} أي "اترك الأمر لطابعة النظام الافتراضية" */
    public static String printerName(DocumentKind kind) {
        String saved = read(printerKey(kind));
        return saved != null ? saved : read(LEGACY_PRINTER_KEY);
    }

    /** @param name اسم الطابعة، أو {@code null} للعودة إلى طابعة النظام الافتراضية */
    public static void setPrinterName(DocumentKind kind, String name) {
        write(printerKey(kind), name);
    }

    /**
     * الطابعة التي ستُستعمل فعلياً لهذا النوع.
     *
     * <p>الطابعة المحفوظة قد تكون فُصلت أو أُعيدت تسميتها، ولا يصح أن يعني ذلك توقّف
     * الإيصالات: نعود إلى طابعة النظام الافتراضية بدل رمي استثناء، وتنبّه الشاشة إلى
     * الغياب عبر {@link #savedPrinterIsMissing(DocumentKind)}.</p>
     *
     * @return الطابعة، أو {@code null} إن لم يكن على الجهاز أي طابعة أصلاً
     */
    public static Printer resolvePrinter(DocumentKind kind) {
        String saved = printerName(kind);
        if (saved != null) {
            for (Printer printer : Printer.getAllPrinters()) {
                if (printer.getName().equals(saved)) {
                    return printer;
                }
            }
        }
        return Printer.getDefaultPrinter();
    }

    /** هل الطابعة المحفوظة لهذا النوع لم تعد موجودة على الجهاز */
    public static boolean savedPrinterIsMissing(DocumentKind kind) {
        String saved = printerName(kind);
        return saved != null
                && Printer.getAllPrinters().stream().noneMatch(printer -> printer.getName().equals(saved));
    }

    // ---------------------------------------------------------------- الورق

    /** اسم مقاس الورق المختار لهذا النوع، أو {@code null} أي مقاس الطابعة الافتراضي */
    public static String paperName(DocumentKind kind) {
        return read(paperKey(kind));
    }

    public static void setPaperName(DocumentKind kind, String name) {
        write(paperKey(kind), name);
    }

    /** المقاسات التي تعلن الطابعة دعمها، مرتَّبة بالاسم لتستقر القائمة بين الفتحات */
    public static List<Paper> supportedPapers(Printer printer) {
        if (printer == null) {
            return List.of();
        }
        List<Paper> papers = new ArrayList<>(printer.getPrinterAttributes().getSupportedPapers());
        papers.sort(Comparator.comparing(Paper::getName));
        return papers;
    }

    /**
     * مقاس الورق المستعمل فعلياً لهذا النوع.
     * المقاس المحفوظ قد لا تدعمه الطابعة الحالية (تغيّرت الطابعة، أو نُقل الإعداد من جهاز
     * آخر)، فنعود إلى مقاس الطابعة الافتراضي بدل إرسال مهمة ترفضها.
     *
     * @return المقاس، أو {@code null} إن لم تكن هناك طابعة
     */
    public static Paper resolvePaper(DocumentKind kind, Printer printer) {
        if (printer == null) {
            return null;
        }
        PrinterAttributes attributes = printer.getPrinterAttributes();
        String saved = paperName(kind);
        if (saved != null) {
            for (Paper paper : attributes.getSupportedPapers()) {
                if (paper.getName().equals(saved)) {
                    return paper;
                }
            }
        }
        return attributes.getDefaultPaper();
    }

    /** هل المقاس المحفوظ لهذا النوع غير مدعوم من الطابعة المختارة */
    public static boolean savedPaperIsMissing(DocumentKind kind, Printer printer) {
        String saved = paperName(kind);
        return saved != null && printer != null
                && printer.getPrinterAttributes().getSupportedPapers().stream()
                        .noneMatch(paper -> paper.getName().equals(saved));
    }

    // ---------------------------------------------------------------- الأسلوب

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
        write(MODE_KEY, mode.name());
    }

    // ------------------------------------------------------- كشوف جاسبر

    /**
     * هل تُرسَل كشوف جاسبر إلى الطابعة مباشرةً بدل فتحها PDF.
     *
     * <p>هذا تفضيل ثانٍ إلى جانب {@link PrintMode}، وليس تكراراً له: {@code PrintMode} يحكم
     * مطبوعات {@code Printing} التي يرسمها البرنامج بنفسه من عُقد JavaFX، وهذا يحكم كشوف
     * {@code .jrxml} التي يبنيها {@code ReportService} - مسارا طباعة مختلفان لا يشتركان في
     * سطر واحد. و{@code PREVIEW} لا مقابل له هنا: فتح الـ PDF <b>هو</b> المعاينة.</p>
     *
     * <p>الافتراضي {@code false} - أي فتح الـ PDF. الطباعة المباشرة تخرج ورقة بلا سؤال، وترقية
     * تشغّلها من تلقاء نفسها تعني ورقاً يخرج من طابعة لم يطلبه أحد. من يريدها يعلّم الخانة.</p>
     */
    public static boolean printsSheetsDirectly() {
        try {
            return prefs().getBoolean(DIRECT_SHEETS_KEY, false);
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void setPrintsSheetsDirectly(boolean direct) {
        write(DIRECT_SHEETS_KEY, String.valueOf(direct));
    }

    /**
     * هل تُطبع ترويسة السنتر - الشعار والاسم والهاتف - في رأس كشوف جاسبر.
     *
     * <p>تفضيل جهاز لا سياسة سنتر رغم أن ما تحمله بيانات السنتر: السبب الذي يُطفئها هو أن
     * الورق المحمّل في <b>هذه</b> الطابعة ورق مطبوعة عليه الترويسة سلفاً، وهذه خاصية الورق
     * في درج طابعة بعينها لا قرارٌ يخصّ السنتر كله - تماماً كمقاس الورق فوقها. ولذلك لا ملف
     * ترحيل لها.</p>
     *
     * <p>الافتراضي {@code true}: هو سلوك النسخ السابقة، وورقةٌ بلا اسم مُصدِرها لا تُعرف
     * بعد شهر من أي سنتر خرجت.</p>
     */
    public static boolean printsCenterHeader() {
        try {
            return prefs().getBoolean(CENTER_HEADER_KEY, true);
        } catch (SecurityException e) {
            return true;
        }
    }

    public static void setPrintsCenterHeader(boolean print) {
        write(CENTER_HEADER_KEY, String.valueOf(print));
    }

    // ---------------------------------------------------------------- التخزين

    private static String printerKey(DocumentKind kind) {
        return "print." + kind.name() + ".printer";
    }

    private static String paperKey(DocumentKind kind) {
        return "print." + kind.name() + ".paper";
    }

    private static String read(String key) {
        try {
            String saved = prefs().get(key, null);
            return saved == null || saved.isBlank() ? null : saved;
        } catch (SecurityException e) {
            // بيئات ويندوز المقيَّدة تمنع قراءة سجل المستخدم؛ الافتراضيات تكفي للتشغيل
            return null;
        }
    }

    private static void write(String key, String value) {
        try {
            Preferences prefs = prefs();
            if (value == null || value.isBlank()) {
                prefs.remove(key);
            } else {
                prefs.put(key, value);
            }
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // فشل الحفظ يعني عودة الاختيار للافتراضي بعد إعادة التشغيل فقط، لا يستحق إسقاط العملية
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(PrintPreferences.class);
    }
}
