package com.codejava.center.service;

import com.codejava.center.domain.Teacher;
import com.codejava.center.util.MoneyUtils;
import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportService {

    // نستخدم DataSource الخاص بـ Spring Boot للاتصال بقاعدة البيانات مباشرة من التقرير
    private final DataSource dataSource;

    /**
     * ذاكرة مؤقتة للتقارير المترجَمة.
     * ترجمة ملف jrxml عملية ثقيلة، وكانت تتكرر مع كل استخراج أو معاينة
     * رغم أن ملفات التصميم لا تتغير أثناء التشغيل.
     */
    private final Map<String, JasperReport> compiledReports = new ConcurrentHashMap<>();

    public ReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * دالة لتوليد التقرير وحفظه كملف PDF
     *
     * @param reportName اسم ملف التقرير (بدون صيغة jrxml)
     * @param parameters المعاملات الممررة للتقرير (مثل رقم المجموعة)
     * @param outputPath مسار حفظ ملف الـ PDF الناتج
     */
    public void generatePdfReport(String reportName, Map<String, Object> parameters, String outputPath) throws Exception {
        JasperReport jasperReport = compile(reportName + ".jrxml");

        // تعبئة التقرير بالبيانات عبر تمرير المعاملات واتصال قاعدة البيانات
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
            JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);
        }
    }

    /**
     * دالة لعرض التقرير مباشرة في نافذة معاينة
     */
    public void showReportPreview(String reportName, Map<String, Object> parameters) throws Exception {
        JasperReport jasperReport = compile(reportName + ".jrxml");

        try (Connection connection = dataSource.getConnection()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);

            // ملف مؤقت يُحذف عند إغلاق البرنامج: التقارير تحوي بيانات مالية
            // وكانت تتراكم في مجلد temp إلى الأبد
            File tempPdfFile = File.createTempFile("center_report_", ".pdf");
            tempPdfFile.deleteOnExit();
            JasperExportManager.exportReportToPdfFile(jasperPrint, tempPdfFile.getAbsolutePath());

            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(tempPdfFile);
            }
        }
    }

    /**
     * توليد تقرير من قائمة كائنات (بدل الاستعلام من قاعدة البيانات) وحفظه
     *
     * @return المسار الفعلي للملف الناتج
     */
    public String exportReportToPdf(String jrxmlFileName, Map<String, Object> parameters,
                                    List<?> data, String outputFileName) throws JRException {
        JasperReport jasperReport = compile(jrxmlFileName);

        JRBeanCollectionDataSource beanDataSource = new JRBeanCollectionDataSource(data);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, beanDataSource);

        String outputPath = resolveOutputDirectory().resolve(outputFileName + ".pdf").toString();
        JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);

        return outputPath;
    }

    public void printTeacherStatement(Teacher teacher, Window ownerWindow) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new IllegalStateException("لا توجد طابعة متاحة على هذا الجهاز.");
        }

        if (!job.showPrintDialog(ownerWindow)) {
            job.cancelJob(); // تحرير مهمة الطباعة عند إلغاء المستخدم
            return;
        }

        VBox printableNode = new VBox(15);
        printableNode.setStyle("-fx-padding: 30; -fx-background-color: white;");

        Label header = new Label("كشف حساب معلم - السنتر التعليمي");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label dateLabel = new Label("تاريخ الإصدار: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        Label teacherInfo = new Label(
                String.format("الاسم: %s%nالمادة: %s%nنوع العمولة: %s%nقيمة العمولة: %s",
                        teacher.getName(), teacher.getSubject(), teacher.getCommissionType(),
                        MoneyUtils.format(teacher.getCommissionValue()))
        );
        teacherInfo.setFont(Font.font("System", 16));

        Label summary = new Label("\n-- تفاصيل الحصص المالية ستدرج هنا لاحقاً --");

        printableNode.getChildren().addAll(header, dateLabel,
                new javafx.scene.control.Separator(), teacherInfo, summary);

        // endJob في كل الحالات: تركها بلا إنهاء عند فشل printPage يُبقي المهمة معلّقة في الطابعة
        try {
            job.printPage(printableNode);
        } finally {
            job.endJob();
        }
    }

    /**
     * ترجمة ملف تصميم التقرير مرة واحدة وحفظ الناتج في الذاكرة.
     */
    private JasperReport compile(String jrxmlFileName) {
        return compiledReports.computeIfAbsent(jrxmlFileName, name -> {
            try (InputStream reportStream = getClass().getResourceAsStream("/reports/" + name)) {
                if (reportStream == null) {
                    throw new IllegalStateException("لم يتم العثور على ملف التقرير: " + name);
                }
                return JasperCompileManager.compileReport(reportStream);
            } catch (JRException e) {
                throw new IllegalStateException("فشل في ترجمة ملف التقرير: " + name, e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * مجلد حفظ التقارير: سطح المكتب إن وُجد، وإلا مجلد المستخدم.
     * المسار "~/Desktop" كان مكتوباً صراحةً فيفشل على ويندوز بلغة غير الإنجليزية
     * أو حين يكون سطح المكتب منقولاً إلى OneDrive.
     */
    private Path resolveOutputDirectory() {
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
