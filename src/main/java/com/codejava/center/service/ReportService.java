package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.Transaction;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.MoneyUtils;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import java.time.LocalDate;
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

    private final SettingsService settingsService;

    public ReportService(DataSource dataSource, SettingsService settingsService) {
        this.dataSource = dataSource;
        this.settingsService = settingsService;
    }

    /**
     * ترويسة موحّدة لكل المطبوعات: شعار السنتر واسمه وهاتفه.
     * هذه البيانات كانت تُجمع في شاشة الإعدادات ولا تظهر في أي مستند.
     */
    private VBox buildHeader(String documentTitle) {
        CenterSettings settings = settingsService.getSettings();

        VBox header = new VBox(6);
        header.setAlignment(Pos.CENTER);

        if (settings.getLogoPath() != null && !settings.getLogoPath().isBlank()) {
            File logoFile = new File(settings.getLogoPath());
            if (logoFile.exists()) {
                ImageView logo = new ImageView(new Image(logoFile.toURI().toString()));
                logo.setFitHeight(70);
                logo.setPreserveRatio(true);
                header.getChildren().add(logo);
            }
        }

        String centerName = settings != null && settings.getCenterName() != null && !settings.getCenterName().isBlank()
                ? settings.getCenterName()
                : "السنتر التعليمي";
        Label nameLabel = new Label(centerName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.getChildren().add(nameLabel);

        if (settings.getCenterPhone() != null && !settings.getCenterPhone().isBlank()) {
            Label phone = new Label("هاتف: " + settings.getCenterPhone());
            phone.setFont(Font.font("System", 12));
            header.getChildren().add(phone);
        }

        Label title = new Label(documentTitle);
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        header.getChildren().addAll(new Separator(), title);

        return header;
    }

    /**
     * طباعة جرد الوردية: الملخّص ثم تفصيل الحركات.
     */
    public void printShiftSummary(LocalDate day, ShiftSummary summary,
                                  List<Transaction> movements, Window ownerWindow) {
        VBox page = new VBox(12);
        page.setStyle("-fx-padding: 30; -fx-background-color: white;");

        page.getChildren().add(buildHeader("جرد الوردية - " + day));

        page.getChildren().addAll(
                summaryLine("الوارد (اشتراكات)", summary.totalIncome()),
                summaryLine("المصروفات", summary.totalExpense()),
                summaryLine("مستحقات معلمين مصروفة", summary.totalTeacherPayouts()),
                new Separator(),
                summaryLine("صافي الدرج", summary.net()),
                new Separator());

        Label detailsTitle = new Label("تفصيل الحركات (" + movements.size() + " حركة)");
        detailsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        page.getChildren().add(detailsTitle);

        for (Transaction t : movements) {
            page.getChildren().add(new Label(String.format("%s   |   %s   |   %s",
                    t.getTransactionDate().format(DateTimeFormatter.ofPattern("hh:mm a")),
                    MoneyUtils.format(t.getAmount()),
                    t.getDescription())));
        }

        Label printedAt = new Label("\nتاريخ الطباعة: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        printedAt.setFont(Font.font("System", 11));
        page.getChildren().add(printedAt);

        printNode(page, ownerWindow);
    }

    /**
     * تقرير المتأخرات: قائمة المدينين ومبالغهم مع بيانات التواصل.
     */
    public void printArrearsReport(List<StudentBalance> arrears, java.math.BigDecimal totalDue, Window ownerWindow) {
        VBox page = new VBox(8);
        page.setStyle("-fx-padding: 30; -fx-background-color: white;");

        page.getChildren().add(buildHeader("تقرير المتأخرات"));

        for (StudentBalance row : arrears) {
            page.getChildren().add(new Label(String.format(
                    "%s   |   باركود: %s   |   ولي الأمر: %s   |   المستحق: %s",
                    row.studentName(),
                    row.barcode() != null ? row.barcode() : "---",
                    row.parentPhone() != null ? row.parentPhone() : "---",
                    MoneyUtils.format(row.amountDue()))));
        }

        page.getChildren().add(new Separator());
        Label total = new Label(String.format("عدد المدينين: %d   -   إجمالي المتأخرات: %s",
                arrears.size(), MoneyUtils.formatWithCurrency(totalDue)));
        total.setFont(Font.font("System", FontWeight.BOLD, 16));
        page.getChildren().add(total);

        Label printedAt = new Label("\nتاريخ الإصدار: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        printedAt.setFont(Font.font("System", 11));
        page.getChildren().add(printedAt);

        printNode(page, ownerWindow);
    }

    /**
     * إيصال استلام نقدية.
     * كان يُبنى داخل شاشة الخزينة بترويسة نصية ثابتة لا تحمل اسم السنتر ولا شعاره
     * رغم أن الإعدادات تجمعهما.
     */
    public void printPaymentReceipt(String studentName, String groupName, java.math.BigDecimal amount,
                                    java.math.BigDecimal newBalance, String description, Window ownerWindow) {
        VBox receipt = new VBox(10);
        receipt.setStyle("-fx-padding: 25; -fx-background-color: white; -fx-border-color: black; -fx-border-width: 1;");

        receipt.getChildren().add(buildHeader("إيصال استلام نقدية"));

        receipt.getChildren().addAll(
                new Label("التاريخ: " + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                new Label("الطالب: " + studentName),
                new Label("المجموعة: " + groupName),
                new Label("البيان: " + description));

        Label paid = new Label("المبلغ المدفوع: " + MoneyUtils.formatWithCurrency(amount));
        paid.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label balance = new Label("الرصيد بعد الدفع: " + MoneyUtils.formatWithCurrency(newBalance));
        balance.setFont(Font.font("System", 14));

        receipt.getChildren().addAll(new Separator(), paid, balance);

        printNode(receipt, ownerWindow);
    }

    /**
     * تقرير حضور وغياب مجموعة خلال فترة.
     */
    public void printAttendanceReport(GroupAttendanceReport report, LocalDate from, LocalDate to,
                                      Window ownerWindow) {
        VBox page = new VBox(8);
        page.setStyle("-fx-padding: 30; -fx-background-color: white;");

        page.getChildren().add(buildHeader("تقرير الحضور والغياب - " + report.groupName()));

        Label period = new Label(String.format("الفترة: من %s إلى %s   -   عدد الحصص المنعقدة: %d",
                from, to, report.totalSessions()));
        period.setFont(Font.font("System", FontWeight.BOLD, 14));
        page.getChildren().addAll(period, new Separator());

        for (AttendanceSummary row : report.rows()) {
            long absences = Math.max(0, report.totalSessions() - row.attended());
            String rate = report.totalSessions() == 0
                    ? "---"
                    : String.format("%.0f%%", (row.attended() * 100.0) / report.totalSessions());

            page.getChildren().add(new Label(String.format(
                    "%s   |   حضر: %d   |   غاب: %d   |   النسبة: %s   |   ولي الأمر: %s",
                    row.studentName(), row.attended(), absences, rate,
                    row.parentPhone() != null ? row.parentPhone() : "---")));
        }

        Label printedAt = new Label("\nتاريخ الإصدار: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        printedAt.setFont(Font.font("System", 11));
        page.getChildren().addAll(new Separator(), printedAt);

        printNode(page, ownerWindow);
    }

    private Label summaryLine(String label, java.math.BigDecimal value) {
        Label line = new Label(label + ": " + MoneyUtils.formatWithCurrency(value));
        line.setFont(Font.font("System", 15));
        return line;
    }

    /**
     * إرسال عقدة للطباعة مع إنهاء المهمة في كل الحالات.
     * ترك المهمة بلا endJob عند الفشل يُبقيها معلّقة في طابور الطابعة.
     */
    private void printNode(javafx.scene.Node node, Window ownerWindow) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new IllegalStateException("لا توجد طابعة متاحة على هذا الجهاز.");
        }

        if (!job.showPrintDialog(ownerWindow)) {
            job.cancelJob();
            return;
        }

        try {
            job.printPage(node);
        } finally {
            job.endJob();
        }
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

    /**
     * كشف حساب معلم يحتوي تفصيل الحصص فعلياً.
     * كان يطبع سطراً واحداً نصه "تفاصيل الحصص المالية ستدرج هنا لاحقاً".
     */
    public void printTeacherStatement(Teacher teacher, List<SessionPayout> sessions, Window ownerWindow) {
        VBox page = new VBox(10);
        page.setStyle("-fx-padding: 30; -fx-background-color: white;");

        page.getChildren().add(buildHeader("كشف حساب معلم"));

        Label teacherInfo = new Label(
                String.format("الاسم: %s%nالمادة: %s%nنوع العمولة: %s%nقيمة العمولة: %s",
                        teacher.getName(), teacher.getSubject(),
                        commissionLabel(teacher.getCommissionType()),
                        MoneyUtils.format(teacher.getCommissionValue()))
        );
        teacherInfo.setFont(Font.font("System", 15));
        page.getChildren().addAll(teacherInfo, new Separator());

        if (sessions.isEmpty()) {
            page.getChildren().add(new Label("لا توجد حصص غير مصروفة لهذا المعلم."));
        } else {
            Label title = new Label("الحصص المغلقة غير المصروفة:");
            title.setFont(Font.font("System", FontWeight.BOLD, 14));
            page.getChildren().add(title);

            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (SessionPayout s : sessions) {
                page.getChildren().add(new Label(String.format(
                        "%s   |   %s   |   حضور: %d   |   إيراد: %s   |   المستحق: %s",
                        s.sessionDate(), s.groupName(), s.attendees(),
                        MoneyUtils.format(s.totalRevenue()), MoneyUtils.format(s.payoutAmount()))));
                total = total.add(s.payoutAmount());
            }

            page.getChildren().add(new Separator());
            Label totalLabel = new Label("إجمالي المستحق: " + MoneyUtils.formatWithCurrency(total));
            totalLabel.setFont(Font.font("System", FontWeight.BOLD, 17));
            page.getChildren().add(totalLabel);
        }

        Label printedAt = new Label("\nتاريخ الإصدار: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        printedAt.setFont(Font.font("System", 11));
        page.getChildren().add(printedAt);

        printNode(page, ownerWindow);
    }

    private String commissionLabel(String type) {
        return switch (type) {
            case "PERCENTAGE" -> "نسبة مئوية";
            case "FIXED_AMOUNT" -> "مبلغ ثابت";
            case "RENT" -> "إيجار قاعة";
            default -> type;
        };
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
