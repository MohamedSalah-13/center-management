package com.codejava.center.util;

import com.codejava.center.core.print.DocumentKind;
import com.codejava.center.service.dto.Sheet;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ما يحدث لورقة جاسبر على <b>هذا الجهاز</b> بعد أن تملأها {@code ReportService}.
 *
 * <p>الخدمة كانت تفعل هذا بنفسها: تملأ ثم تطبع أو تكتب ملفاً مؤقتاً في الدالة نفسها. فصلُ
 * الاثنين هو المقصود — الخدمة تصف ورقة ({@link Sheet})، وهذا الصنف يقرّر ما يُفعل بها على
 * جهازٍ له طابعة ومجلد مؤقت وعارض PDF. خادمٌ يطلب نفس الورقة ليردّها في جواب HTTP لا يمرّ
 * من هنا أصلاً؛ يأخذ {@link Sheet#toPdf()} ويمضي.</p>
 *
 * <p>وهو يقرأ تفضيلات الجهاز من {@link PrintPreferences} مباشرةً لا عبر واجهة في النواة،
 * كجاره {@link Printing} تماماً: لا حدّ وحدات بين ملفين في حزمة واحدة، والواجهة تُوضع حيث
 * يُعبر حدٌّ لا حيث لا يوجد.</p>
 *
 * <p><b>والخيوط نصفان، فانتبه لأيّهما تستدعي:</b> {@link #deliver(Sheet)} و
 * {@link #save(Sheet, String)} يطبعان ويكتبان على القرص فمكانهما خيط خلفي
 * ({@link FxAsync})؛ و{@link #show(SheetDelivery)} ينشئ نوافذ JavaFX فمكانه خيط الواجهة.
 * والنمط في كل شاشة واحد:</p>
 *
 * <pre>{@code
 * FxAsync.supply(() -> Sheets.deliver(reportService.arrearsSheet(rows, total)),
 *         Sheets::show,
 *         error -> Dialogs.error(I18n.get("common.printError"), FxAsync.messageOf(error)));
 * }</pre>
 */
public final class Sheets {

    private Sheets() {
    }

    // ------------------------------------------------------------ خيط خلفي

    /**
     * يسلّم الورقة حسب تفضيل هذا الجهاز: إلى الطابعة رأساً، أو ملف PDF مؤقت.
     *
     * <p>القرار هنا لا في كل شاشة تطبع كشفاً: هو تفضيل واحد
     * ({@link PrintPreferences#printsSheetsDirectly()})، وتكراره في المتحكّمات يعني شاشةً
     * تنساه فتخالف بقية البرنامج بلا أن يلاحظ أحد.</p>
     *
     * <p>الملف مؤقت ويُحذف عند إغلاق البرنامج: الكشوف تحمل أسماء طلاب وأرقام أولياء
     * أمورهم، فلا تُترك متراكمة في مجلد المستخدم بعد طباعتها. وما يُحفظ عن قصد ليُبقى —
     * الكارنيهات مثلاً — يمرّ بـ {@link #save(Sheet, String)} لا من هنا.</p>
     */
    public static SheetDelivery deliver(Sheet sheet) {
        if (PrintPreferences.printsSheetsDirectly()) {
            return SheetDelivery.printed(sendToPrinter(sheet));
        }
        try {
            File pdf = File.createTempFile(sheet.fileNamePrefix(), ".pdf");
            pdf.deleteOnExit();
            Files.write(pdf.toPath(), sheet.toPdf());
            return SheetDelivery.exported(pdf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * يحفظ الورقة ملفاً باسم يختاره المستخدم ليبقى، في مجلد التصدير.
     *
     * <p>مقابل {@link #deliver(Sheet)} لا حالة منه: ورقةٌ تُطبع الآن تذهب إلى الطابعة أو
     * إلى ملف مؤقت، وورقةٌ تُحفظ لتُطبع لاحقاً على ورق كارنيهات تحتاج مكاناً يجده صاحبها
     * بعد أسبوع واسماً يعرفه.</p>
     *
     * @param baseName اسم الملف بلا امتداد
     * @return الملف المكتوب
     */
    public static File save(Sheet sheet, String baseName) {
        Path target = outputDirectory().resolve(baseName + ".pdf");
        try {
            Files.write(target, sheet.toPdf());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toFile();
    }

    // --------------------------------------------------------- خيط الواجهة

    /**
     * سطرٌ واحد لكل شاشة تطبع كشفاً: "أُرسل إلى الطابعة فلانة"، أو فتح الـ PDF.
     *
     * <p>كُتب هنا لا في المتحكّمات لأن الشاشة الثانية التي طبعت كشفاً نسخت الأولى، والثالثة
     * كانت ستنسخ الثانية - ومعها احتمال أن تنسى إحداها أن تقول شيئاً حين يفشل الفتح.</p>
     */
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

    // ------------------------------------------------------------ الطابعة

    /**
     * يرسل الورقة إلى الطابعة المختارة لنوعها.
     *
     * <p>الطابعة تُلتمس بالاسم بين خدمات الطباعة: جاسبر يطبع عبر {@code javax.print} بينما
     * تختار الشاشة {@code javafx.print.Printer}، والاسمان يأتيان من مُخطِّط الطباعة نفسه في
     * ويندوز فيتطابقان. ولولا الالتماس لذهب الكشف إلى طابعة النظام الافتراضية بينما تعلن
     * شاشة الإعدادات طابعةً أخرى.</p>
     *
     * <p>ولا نافذة طابعة تُعرض: نوافذ {@code javax.print} نوافذ AWT، وهذه الدالة تجري على
     * خيط خلفي - وفتح نافذة AWT منه مقامرة. ومن أراد النافذة يترك الخانة غير معلَّمة فيفتح
     * الـ PDF ويطبع منه.</p>
     *
     * @return اسم الطابعة التي استُلم الكشف عليها، ليُقال للمستخدم أين يذهب ليأخذه
     */
    private static String sendToPrinter(Sheet sheet) {
        PrintService service = resolvePrintService(sheet.kind());

        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintService(service);
        configuration.setDisplayPageDialog(false);
        configuration.setDisplayPrintDialog(false);

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        exporter.setExporterInput(new SimpleExporterInput(sheet.print()));
        exporter.setConfiguration(configuration);

        try {
            exporter.exportReport();
        } catch (JRException e) {
            throw Sheet.generationFailed(e);
        }
        return service.getName();
    }

    /**
     * خدمة الطباعة المقابلة للطابعة المختارة لهذا النوع من المستندات، أو الافتراضية.
     *
     * <p>النوع لا يُهمَل: الجهاز الواحد في السنتر قد يكون موصولاً بطابعة حرارية للإيصالات
     * وطابعة A4 للتقارير معاً، وإرسال إيصال إلى طابعة التقارير يعني ورقة A4 كاملة تخرج
     * لأجل سبعة أسطر - أو العكس: كشف مجموعة يخرج من رول 80mm مقصوصاً من طرفيه.</p>
     *
     * <p>وغياب أي طابعة يُقال صراحةً: الطباعة المباشرة بلا طابعة تفشل بصمت في أعماق جاسبر.</p>
     */
    private static PrintService resolvePrintService(DocumentKind kind) {
        String chosen = PrintPreferences.printerName(kind);
        if (chosen != null) {
            for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
                if (service.getName().equals(chosen)) {
                    return service;
                }
            }
        }

        PrintService fallback = PrintServiceLookup.lookupDefaultPrintService();
        if (fallback == null) {
            throw new IllegalStateException(I18n.get("error.report.noPrinter"));
        }
        return fallback;
    }

    // ------------------------------------------------------------- المجلد

    /**
     * مجلد حفظ الملفات: سطح المكتب إن وُجد، وإلا مجلد المستخدم.
     * المسار "~/Desktop" كان مكتوباً صراحةً فيفشل على ويندوز بلغة غير الإنجليزية
     * أو حين يكون سطح المكتب منقولاً إلى OneDrive.
     */
    private static Path outputDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path desktop = home.resolve("Desktop");

        if (Files.isDirectory(desktop)) {
            return desktop;
        }

        Path oneDriveDesktop = home.resolve("OneDrive").resolve("Desktop");
        if (Files.isDirectory(oneDriveDesktop)) {
            return oneDriveDesktop;
        }

        return home;
    }
}
