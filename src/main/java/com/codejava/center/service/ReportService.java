package com.codejava.center.service;

import com.codejava.center.domain.Teacher;
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
import java.io.InputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    // نستخدم DataSource الخاص بـ Spring Boot للاتصال بقاعدة البيانات مباشرة من التقرير
    private final DataSource dataSource;

    public ReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * دالة لتوليد التقرير وحفظه كملف PDF
     * @param reportName اسم ملف التقرير (بدون صيغة jrxml)
     * @param parameters المعاملات الممررة للتقرير (مثل رقم المجموعة)
     * @param outputPath مسار حفظ ملف الـ PDF الناتج
     */
    public void generatePdfReport(String reportName, Map<String, Object> parameters, String outputPath) throws Exception {
        // 1. قراءة ملف تصميم التقرير من مجلد resources
        InputStream reportStream = getClass().getResourceAsStream("/reports/" + reportName + ".jrxml");
        if (reportStream == null) {
            throw new RuntimeException("لم يتم العثور على ملف التقرير: " + reportName);
        }

        // 2. عمل Compile للتقرير
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        // 3. تعبئة التقرير بالبيانات عبر تمرير المعاملات واتصال قاعدة البيانات
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);

            // 4. تصدير التقرير كملف PDF
            JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);
        }
    }
    public void printTeacherStatement(Teacher teacher, Window ownerWindow) {
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job != null && job.showPrintDialog(ownerWindow)) {
            // بناء تصميم الإيصال/التقرير للطباعة
            VBox printableNode = new VBox(15);
            printableNode.setStyle("-fx-padding: 30; -fx-background-color: white;");

            Label header = new Label("كشف حساب معلم - السنتر التعليمي");
            header.setFont(Font.font("System", FontWeight.BOLD, 24));

            Label dateLabel = new Label("تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            Label teacherInfo = new Label(
                    String.format("الاسم: %s\nالمادة: %s\nنوع العمولة: %s\nقيمة العمولة: %s",
                            teacher.getName(), teacher.getSubject(), teacher.getCommissionType(), teacher.getCommissionValue())
            );
            teacherInfo.setFont(Font.font("System", 16));

            // يمكن لاحقاً حقن SessionRepository هنا لجلب إجمالي الحصص وإيراداتها
            Label summary = new Label("\n-- تفاصيل الحصص المالية ستدرج هنا لاحقاً --");

            printableNode.getChildren().addAll(header, dateLabel, new javafx.scene.control.Separator(), teacherInfo, summary);

            // تنفيذ الطباعة
            boolean success = job.printPage(printableNode);
            if (success) {
                job.endJob();
            }
        }
    }

    /**
     * دالة لعرض التقرير مباشرة في نافذة معاينة JasperViewer
     * @param reportName اسم ملف التقرير (بدون صيغة jrxml)
     * @param parameters المعاملات الممررة للتقرير
     */
    public void showReportPreview(String reportName, Map<String, Object> parameters) throws Exception {
        InputStream reportStream = getClass().getResourceAsStream("/reports/" + reportName + ".jrxml");
        if (reportStream == null) {
            throw new RuntimeException("لم يتم العثور على ملف التقرير: " + reportName);
        }

        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        try (Connection connection = dataSource.getConnection()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);

            // إنشاء ملف PDF مؤقت
            java.io.File tempPdfFile = java.io.File.createTempFile("center_report_", ".pdf");
            JasperExportManager.exportReportToPdfFile(jasperPrint, tempPdfFile.getAbsolutePath());

            // فتحه تلقائياً بواسطة البرنامج الافتراضي في نظام التشغيل (عبر واجهة JavaFX/Desktop الآمنة)
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(tempPdfFile);
            }
        }
    }

    /**
     * توليد تقرير بصيغة PDF وحفظه في مسار محدد
     */
    public String exportReportToPdf(String jrxmlFileName, Map<String, Object> parameters, List<?> data, String outputFileName) throws JRException {

        // 1. قراءة ملف التصميم من مجلد resources
        InputStream reportStream = getClass().getResourceAsStream("/reports/" + jrxmlFileName);
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        // 2. تحويل قائمة الكيانات إلى DataSource
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);

        // 3. تعبئة التقرير بالبيانات
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // 4. تحديد مسار الحفظ (سطح المكتب كمثال) وتصدير الملف
        String outputPath = System.getProperty("user.home") + "/Desktop/" + outputFileName + ".pdf";
        JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);

        return outputPath;
    }
}