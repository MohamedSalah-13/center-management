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
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.PrintDocument;
import com.codejava.center.util.Printing;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import java.util.function.Supplier;

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
     * مصنع الترويسة الموحّدة: شعار السنتر واسمه وهاتفه فوق كل صفحة.
     *
     * <p>يُرجع {@link Supplier} لا عقدة جاهزة لأن الترويسة تتكرر في أعلى كل صفحة، والعقدة
     * الواحدة لا تُضاف إلى أكثر من أب في JavaFX.</p>
     *
     * <p>بيانات السنتر والشعار تُقرأ مرة واحدة هنا لا داخل المُنتِج: استدعاؤه يقع مرة لكل
     * صفحة، وتقرير من عشر صفحات كان سيعني عشرة استعلامات لقاعدة البيانات وعشر قراءات
     * لملف الشعار من القرص.</p>
     */
    private Supplier<Node> headerFactory(String documentTitle) {
        CenterSettings settings = settingsService.getSettings();

        String centerName = settings != null && settings.getCenterName() != null
                && !settings.getCenterName().isBlank()
                ? settings.getCenterName()
                : I18n.get("report.header.defaultCenterName");
        String phone = settings != null ? settings.getCenterPhone() : null;
        Image logo = loadLogo(settings);

        return () -> {
            VBox header = new VBox(6);
            header.setAlignment(Pos.CENTER);

            if (logo != null) {
                ImageView view = new ImageView(logo);
                view.setFitHeight(70);
                view.setPreserveRatio(true);
                header.getChildren().add(view);
            }

            Label nameLabel = new Label(centerName);
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
            header.getChildren().add(nameLabel);

            if (phone != null && !phone.isBlank()) {
                Label phoneLabel = new Label(I18n.format("report.header.phone", phone));
                phoneLabel.setFont(Font.font("System", 12));
                header.getChildren().add(phoneLabel);
            }

            Label title = new Label(documentTitle);
            title.setFont(Font.font("System", FontWeight.BOLD, 18));
            header.getChildren().addAll(new Separator(), title);

            return header;
        };
    }

    /** الشعار قد يكون غير مضبوط أو نُقل ملفه؛ المطبوعة تخرج بلا شعار ولا تفشل */
    private Image loadLogo(CenterSettings settings) {
        if (settings == null || settings.getLogoPath() == null || settings.getLogoPath().isBlank()) {
            return null;
        }
        File logoFile = new File(settings.getLogoPath());
        return logoFile.exists() ? new Image(logoFile.toURI().toString()) : null;
    }

    /** سطر تاريخ الطباعة في ذيل كل مستند */
    private Label stamp(String key) {
        Label label = new Label(I18n.format(key,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        label.setFont(Font.font("System", 11));
        return label;
    }

    /**
     * طباعة جرد الوردية: الملخّص ثم تفصيل الحركات.
     */
    public void printShiftSummary(LocalDate day, ShiftSummary summary,
                                  List<Transaction> movements, Window ownerWindow) {
        PrintDocument document = PrintDocument.report()
                .header(headerFactory(I18n.format("report.shift.title", day)));

        document.add(
                summaryLine(I18n.get("shift.income"), summary.totalIncome()),
                summaryLine(I18n.get("shift.expenses"), summary.totalExpense()),
                summaryLine(I18n.get("shift.payouts"), summary.totalTeacherPayouts()),
                new Separator(),
                summaryLine(I18n.get("shift.net"), summary.net()),
                new Separator());

        Label detailsTitle = new Label(I18n.format("report.shift.details", movements.size()));
        detailsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        document.add(detailsTitle);

        for (Transaction t : movements) {
            document.add(new Label(I18n.format("report.shift.row",
                    t.getTransactionDate().format(DateTimeFormatter.ofPattern("hh:mm a")),
                    MoneyUtils.format(t.getAmount()),
                    t.getDescription())));
        }

        document.add(stamp("report.printedAt"));
        Printing.print(document, ownerWindow);
    }

    /**
     * تقرير المتأخرات: قائمة المدينين ومبالغهم مع بيانات التواصل.
     */
    public void printArrearsReport(List<StudentBalance> arrears, java.math.BigDecimal totalDue, Window ownerWindow) {
        PrintDocument document = PrintDocument.report()
                .header(headerFactory(I18n.get("report.arrears.title")));

        String none = I18n.get("common.none");
        for (StudentBalance row : arrears) {
            document.add(new Label(I18n.format("report.arrears.row",
                    row.studentName(),
                    row.barcode() != null ? row.barcode() : none,
                    row.parentPhone() != null ? row.parentPhone() : none,
                    MoneyUtils.format(row.amountDue()))));
        }

        Label total = new Label(I18n.format("report.arrears.total",
                arrears.size(), MoneyUtils.formatWithCurrency(totalDue)));
        total.setFont(Font.font("System", FontWeight.BOLD, 16));

        document.add(new Separator(), total, stamp("report.issuedAt"));
        Printing.print(document, ownerWindow);
    }

    /**
     * إيصال استلام نقدية.
     * كان يُبنى داخل شاشة الخزينة بترويسة نصية ثابتة لا تحمل اسم السنتر ولا شعاره
     * رغم أن الإعدادات تجمعهما.
     */
    public void printPaymentReceipt(String studentName, String groupName, java.math.BigDecimal amount,
                                    java.math.BigDecimal newBalance, String description, Window ownerWindow) {
        // إيصال لا تقرير: صفحة واحدة على الرول بلا ترقيم، وبورق الإيصالات لا ورق التقارير
        PrintDocument receipt = PrintDocument.receipt()
                .header(headerFactory(I18n.get("report.receipt.title")));

        receipt.add(
                new Label(I18n.format("report.receipt.date", LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))),
                new Label(I18n.format("report.receipt.student", studentName)),
                new Label(I18n.format("report.receipt.group", groupName)),
                new Label(I18n.format("report.receipt.description", description)));

        Label paid = new Label(I18n.format("report.receipt.amount", MoneyUtils.formatWithCurrency(amount)));
        paid.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label balance = new Label(I18n.format("report.receipt.balance", MoneyUtils.formatWithCurrency(newBalance)));
        balance.setFont(Font.font("System", 14));

        receipt.add(new Separator(), paid, balance);
        Printing.print(receipt, ownerWindow);
    }

    /**
     * تقرير حضور وغياب مجموعة خلال فترة.
     */
    public void printAttendanceReport(GroupAttendanceReport report, LocalDate from, LocalDate to,
                                      Window ownerWindow) {
        PrintDocument document = PrintDocument.report()
                .header(headerFactory(I18n.format("report.attendance.title", report.groupName())));

        Label period = new Label(I18n.format("report.attendance.period",
                from, to, report.totalSessions()));
        period.setFont(Font.font("System", FontWeight.BOLD, 14));
        document.add(period, new Separator());

        String none = I18n.get("common.none");
        for (AttendanceSummary row : report.rows()) {
            long absences = Math.max(0, report.totalSessions() - row.attended());
            String rate = report.totalSessions() == 0
                    ? none
                    : String.format("%.0f%%", (row.attended() * 100.0) / report.totalSessions());

            document.add(new Label(I18n.format("report.attendance.row",
                    row.studentName(), row.attended(), absences, rate,
                    row.parentPhone() != null ? row.parentPhone() : none)));
        }

        document.add(new Separator(), stamp("report.issuedAt"));
        Printing.print(document, ownerWindow);
    }

    /**
     * طباعة سجل المراقبة كما هو معروض على الشاشة.
     *
     * <p>الغرض منها المراجعة خارج الجهاز: ورقة يوقّعها المحاسب أو تُحفظ في ملف، لا يمسّها
     * ما يجري على قاعدة البيانات بعدها.</p>
     *
     * <p>كل حدث كتلة واحدة من سطرين: التقسيم في {@link Printing} يقع بين الكتل لا داخلها،
     * فلا ينتهي وجه الصفحة بنصف حدث - وسطر مراقبة مبتور أسوأ من غيابه.</p>
     */
    public void printAuditReport(List<com.codejava.center.domain.AuditLog> events,
                                 LocalDate from, LocalDate to, Window ownerWindow) {
        PrintDocument document = PrintDocument.report()
                .header(headerFactory(I18n.get("report.audit.title")));

        Label period = new Label(I18n.format("report.audit.period", from, to, events.size()));
        period.setFont(Font.font("System", FontWeight.BOLD, 14));
        document.add(period, new Separator());

        DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String none = I18n.get("common.none");

        for (com.codejava.center.domain.AuditLog event : events) {
            VBox block = new VBox(2);

            Label headline = new Label(I18n.format("report.audit.row",
                    event.getOccurredAt().format(timestamp),
                    event.getActorUsername() == null ? I18n.get("audit.systemActor") : event.getActorUsername(),
                    event.getAction().getDisplayName(),
                    event.getEntityLabel() == null ? none : event.getEntityLabel(),
                    event.getAmount() == null ? none : MoneyUtils.format(event.getAmount()),
                    I18n.get(event.isSuccessful() ? "audit.status.ok" : "audit.status.failed")));
            headline.setFont(Font.font("System", 12));
            block.getChildren().add(headline);

            if (event.getDetails() != null && !event.getDetails().isBlank()) {
                Label details = new Label(I18n.format("report.audit.details", event.getDetails()));
                details.setFont(Font.font("System", 10));
                block.getChildren().add(details);
            }

            document.add(block);
        }

        document.add(new Separator(), stamp("report.issuedAt"));
        Printing.print(document, ownerWindow);
    }

    private Label summaryLine(String label, java.math.BigDecimal value) {
        Label line = new Label(I18n.format("report.summaryLine", label, MoneyUtils.formatWithCurrency(value)));
        line.setFont(Font.font("System", 15));
        return line;
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
        PrintDocument document = PrintDocument.report()
                .header(headerFactory(I18n.get("report.teacher.title")));

        Label teacherInfo = new Label(
                I18n.format("report.teacher.info",
                        teacher.getName(), teacher.getSubject(),
                        CommissionTypes.displayName(teacher.getCommissionType()),
                        MoneyUtils.format(teacher.getCommissionValue()))
        );
        teacherInfo.setFont(Font.font("System", 15));
        document.add(teacherInfo, new Separator());

        if (sessions.isEmpty()) {
            document.add(new Label(I18n.get("report.teacher.noSessions")));
        } else {
            Label title = new Label(I18n.get("report.teacher.sessionsTitle"));
            title.setFont(Font.font("System", FontWeight.BOLD, 14));
            document.add(title);

            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (SessionPayout s : sessions) {
                document.add(new Label(I18n.format("report.teacher.row",
                        s.sessionDate(), s.groupName(), s.attendees(),
                        MoneyUtils.format(s.totalRevenue()), MoneyUtils.format(s.payoutAmount()))));
                total = total.add(s.payoutAmount());
            }

            Label totalLabel = new Label(I18n.format("report.teacher.total", MoneyUtils.formatWithCurrency(total)));
            totalLabel.setFont(Font.font("System", FontWeight.BOLD, 17));
            document.add(new Separator(), totalLabel);
        }

        document.add(stamp("report.issuedAt"));
        Printing.print(document, ownerWindow);
    }

    /**
     * ترجمة ملف تصميم التقرير مرة واحدة وحفظ الناتج في الذاكرة.
     */
    private JasperReport compile(String jrxmlFileName) {
        return compiledReports.computeIfAbsent(jrxmlFileName, name -> {
            try (InputStream reportStream = getClass().getResourceAsStream("/reports/" + name)) {
                if (reportStream == null) {
                    throw new IllegalStateException(I18n.format("error.report.fileNotFound", name));
                }
                return JasperCompileManager.compileReport(reportStream);
            } catch (JRException e) {
                throw new IllegalStateException(I18n.format("error.report.compileFailed", name), e);
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
