package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.EnrollmentReportRow;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.service.dto.GroupListRow;
import com.codejava.center.service.dto.GroupRosterRow;
import com.codejava.center.service.dto.IdCardRow;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.service.dto.SheetDelivery;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.DocumentKind;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.PrintDocument;
import com.codejava.center.util.PrintPreferences;
import com.codejava.center.util.Printing;
import com.codejava.center.util.WeekDays;
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
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
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
     * كشف المجموعات كما تعرضها الشاشة بعد التصفية.
     *
     * <p>وصف التصفية يُطبع في أعلى الورقة: كشف يقول "مجموعات المعلم فلان يوم السبت"
     * يُقرأ بعد شهر، وكشف بلا وصف يبدو أنه كل مجموعات السنتر وليس كذلك.</p>
     */
    public SheetDelivery deliverGroupsList(List<CourseGroup> groups, Map<Long, Long> memberCounts,
                                           String filterDescription) {
        String none = I18n.get("common.none");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.groups.title"));
        parameters.put("SCOPE", I18n.format("report.groups.scope", filterDescription, groups.size()));
        parameters.put("COL_NAME", I18n.get("group.col.name"));
        parameters.put("COL_TEACHER", I18n.get("group.col.teacher"));
        parameters.put("COL_LEVEL", I18n.get("group.col.level"));
        parameters.put("COL_DAYS", I18n.get("group.col.days"));
        parameters.put("COL_TIME", I18n.get("group.col.time"));
        parameters.put("COL_MEMBERS", I18n.get("group.col.members"));
        parameters.put("COL_PRICE", I18n.get("group.col.price"));
        parameters.put("NO_ROWS", I18n.get("report.groups.noGroups"));

        List<GroupListRow> rows = groups.stream()
                .map(group -> new GroupListRow(
                        group.getName(),
                        group.getTeacher().getName(),
                        group.getSchoolLevel() == null ? none : group.getSchoolLevel().getDisplayName(),
                        WeekDays.describe(group.getMeetingDays()),
                        WeekDays.describeRange(group.getStartTime(), group.getEndTime()),
                        I18n.format("group.membersOf",
                                memberCounts.getOrDefault(group.getId(), 0L),
                                group.getMaxCapacity() == null ? none : group.getMaxCapacity()),
                        MoneyUtils.format(group.getSessionPrice())))
                .toList();

        return deliver(fill("GroupsList.jrxml", parameters, rows), "groups_list_");
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
     * كشف مجموعة: بياناتها ثم مشتركوها الحاليون، ومع كلٍّ حصص مدة اشتراكه وما حضره منها.
     *
     * <p>الصفوف تصل من {@code EnrollmentService.getRoster}، وهو الفرق الذي يخصّ من يقرأ
     * الورقة: استعلامه يقصر الكشف على العضويات السارية، ويعدّ حصص كلٍّ من يوم التحاقه لا
     * من إنشاء المجموعة. الكشف يُقرأ ليُعرف من ينقطع، ومن التحق الأسبوع الماضي ليس
     * منقطعاً.</p>
     *
     * <p>ونصوص الورقة كلها تُبنى هنا بـ {@code I18n}: عنوانها وسطر بيانات المجموعة وعناوين
     * أعمدتها وذيلها، فلا يكون على الشاشة أن تتذكّر اثني عشر معاملاً ولا أن تعرف أسماءها.</p>
     */
    public SheetDelivery deliverGroupRoster(CourseGroup group, List<MembershipRow> members) {
        String none = I18n.get("common.none");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.format("report.group.title", group.getName()));
        parameters.put("GROUP_INFO", I18n.format("report.group.info",
                group.getTeacher().getName(),
                group.getSchoolLevel() == null ? none : group.getSchoolLevel().getDisplayName(),
                WeekDays.describe(group.getMeetingDays()),
                WeekDays.describeRange(group.getStartTime(), group.getEndTime()),
                MoneyUtils.formatWithCurrency(group.getSessionPrice()),
                members.size(),
                group.getMaxCapacity() == null ? none : group.getMaxCapacity()));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_NAME", I18n.get("report.group.col.name"));
        parameters.put("COL_BARCODE", I18n.get("report.group.col.barcode"));
        parameters.put("COL_PARENT", I18n.get("report.group.col.parent"));
        parameters.put("COL_JOINED", I18n.get("report.group.col.joined"));
        parameters.put("COL_ATTENDANCE", I18n.get("report.group.col.attendance"));
        parameters.put("COL_RATE", I18n.get("student.col.attendanceRate"));
        parameters.put("NO_ROWS", I18n.get("report.group.noMembers"));

        int[] serial = {0};
        List<GroupRosterRow> rows = members.stream()
                .map(member -> new GroupRosterRow(
                        String.valueOf(++serial[0]),
                        member.studentName(),
                        member.barcode() == null ? none : member.barcode(),
                        member.parentPhone() == null ? none : member.parentPhone(),
                        String.valueOf(member.joinDate()),
                        I18n.format("report.group.attendanceOf",
                                member.sessionsAttended(), member.sessionsHeld()),
                        member.attendanceRate() == null ? none : member.attendanceRate() + "%"))
                .toList();

        return deliver(fill("GroupStudents.jrxml", parameters, rows), "group_roster_");
    }

    /**
     * يبني ورقة جاسبر من قائمة كائنات.
     * الملء وحده هنا، والتسليم في {@link #deliver}: أيّهما تغيّر لا يمسّ الآخر.
     */
    private JasperPrint fill(String template, Map<String, Object> parameters, List<?> rows) {
        try {
            return JasperFillManager.fillReport(compile(template), parameters,
                    new JRBeanCollectionDataSource(rows));
        } catch (JRException e) {
            throw generationFailed(e);
        }
    }

    /**
     * يسلّم الورقة حسب تفضيل هذا الجهاز: إلى الطابعة رأساً، أو ملف PDF مؤقت.
     *
     * <p>القرار هنا لا في كل شاشة تطبع كشفاً: هو تفضيل واحد
     * ({@code PrintPreferences.printsSheetsDirectly})، وتكراره في المتحكّمات يعني شاشةً
     * تنساه فتخالف بقية البرنامج بلا أن يلاحظ أحد.</p>
     *
     * <p>الملف مؤقت ويُحذف عند إغلاق البرنامج: الكشوف تحمل أسماء طلاب وأرقام أولياء
     * أمورهم، فلا تُترك متراكمة في مجلد المستخدم بعد طباعتها.</p>
     */
    private SheetDelivery deliver(JasperPrint print, String tempPrefix) {
        if (PrintPreferences.printsSheetsDirectly()) {
            return SheetDelivery.printed(sendToPrinter(print));
        }
        try {
            File pdf = File.createTempFile(tempPrefix, ".pdf");
            pdf.deleteOnExit();
            JasperExportManager.exportReportToPdfFile(print, pdf.getAbsolutePath());
            return SheetDelivery.exported(pdf);
        } catch (JRException e) {
            throw generationFailed(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
     * كارنيهات الطلاب المعروضين، بترويسة السنتر فوقها.
     *
     * <p>الطلاب لا يُمرَّرون إلى ملف التصميم كما هم: مرحلة الطالب قيمة {@code enum} في
     * الكيان بينما التصميم يعلن الحقل نصاً - وكان ذلك يُسقط التصدير - واسمها المعروض
     * ترجمةٌ لا {@code toString()}. {@link IdCardRow} هو ما يقف بينهما.</p>
     *
     * <p>الترويسة تصل عبر {@link #withCenterHeader(Map)} كما في كل تقرير جاسبر: ملف
     * التصميم يعلن معاملاتها ويضع عنصر التقرير الفرعي، ولا يرسم شعاراً بنفسه.</p>
     *
     * @return المسار الفعلي لملف الـ PDF الناتج
     */
    public String exportStudentIdCards(List<Student> students, String outputFileName) throws JRException {
        Map<String, Object> parameters = withCenterHeader(new java.util.HashMap<>());
        parameters.put("CARD_TITLE", I18n.get("report.idCards.cardTitle"));

        List<IdCardRow> cards = students.stream()
                .map(student -> new IdCardRow(
                        student.getName(),
                        student.getBarcode(),
                        student.getSchoolLevel() == null ? null : student.getSchoolLevel().getDisplayName()))
                .toList();

        return exportReportToPdf("StudentIdCards.jrxml", parameters, cards, outputFileName);
    }

    /**
     * كشف اشتراكات طالب: تقرير جاسبر يُملأ من العضويات المعروضة على الشاشة نفسها.
     *
     * <p>البيانات تصل قائمةً جاهزة لا استعلاماً داخل الـ jrxml: ما يُطبع هو ما يراه
     * المستخدم في الجدول أمامه، وسؤال قاعدة البيانات مرة أخرى يفتح باب أن يختلف
     * الاثنان - والورقة التي تخالف الشاشة تُفقد الثقة في الاثنتين معاً.</p>
     *
     * <p>وكل نصّ في الورقة يُبنى هنا بـ {@code I18n} ويُمرَّر معاملاً: ملف التصميم لا
     * تراه حزم النصوص، فنصٌّ مكتوب داخله يخرج بلغته مهما كانت لغة البرنامج.</p>
     *
     */
    public SheetDelivery deliverStudentEnrollments(String studentName, String studentDetails,
                                                   List<MembershipRow> memberships) {
        return deliver(fillStudentEnrollments(studentName, studentDetails, memberships),
                "student_enrollments_");
    }

    /**
     * الورقة مُرسَلةً إلى الطابعة بلا ملف وسيط ولا نافذة.
     *
     * <p>الطابعة هي المختارة لـ {@link DocumentKind#REPORT} في الإعدادات، تُلتمس بالاسم بين
     * خدمات الطباعة: جاسبر يطبع عبر {@code javax.print} بينما تختار الشاشة {@code javafx.print
     * .Printer}، والاسمان يأتيان من مُخطِّط الطباعة نفسه في ويندوز فيتطابقان. ولولا الالتماس
     * لذهب الكشف إلى طابعة النظام الافتراضية بينما تعلن شاشة الإعدادات طابعةً أخرى.</p>
     *
     * <p>ولا نافذة طابعة تُعرض: نوافذ {@code javax.print} نوافذ AWT، وهذه الدالة تجري على خيط
     * خلفي - وفتح نافذة AWT منه مقامرة. ومن أراد النافذة يترك الخانة غير معلَّمة فيفتح الـ PDF
     * ويطبع منه.</p>
     *
     * @return اسم الطابعة التي استُلم الكشف عليها، ليُقال للمستخدم أين يذهب ليأخذه
     */
    private String sendToPrinter(JasperPrint print) {
        PrintService service = resolvePrintService();

        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintService(service);
        configuration.setDisplayPageDialog(false);
        configuration.setDisplayPrintDialog(false);

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setConfiguration(configuration);

        try {
            exporter.exportReport();
        } catch (JRException e) {
            throw generationFailed(e);
        }
        return service.getName();
    }

    /**
     * خدمة الطباعة المقابلة للطابعة المختارة للتقارير، أو الافتراضية.
     * غياب أي طابعة يُقال صراحةً: الطباعة المباشرة بلا طابعة تفشل بصمت في أعماق جاسبر.
     */
    private PrintService resolvePrintService() {
        String chosen = PrintPreferences.printerName(DocumentKind.REPORT);
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

    /**
     * ترويسة السنتر لأي تقرير جاسبر: شعارٌ يميناً، واسمٌ وهاتفٌ يساراً.
     *
     * <p><b>هذه هي الطريقة التي يُبنى بها كل تقرير جاسبر جديد.</b> ملف التصميم يعلن الخمسة
     * أدناه معاملاتٍ ويضع في {@code pageHeader} عنصر {@code subreport} واحداً يشير إلى
     * {@code $P{HEADER_REPORT}} - انسخ الفرقة من {@code StudentEnrollments.jrxml} - ثم يمرّ
     * الملء من هنا. لا شعار يُرسم ولا اسم يُكتب في ملف التصميم نفسه: نسخُ الكتلة في كل ملف
     * يعني أن تغيير مقاس الشعار تحريرٌ في عشرة ملفات، ونسيان واحد لا يظهر إلا في ورقة.</p>
     *
     * <p>الشرط مكتوب على الفرقة لا على العنصر، فتنطوي بارتفاعها كله حين يُطفئ المستخدم
     * الترويسة بدل أن تترك فراغاً أبيض في رأس كل صفحة.</p>
     *
     * <p>وبيانات السنتر تُقرأ مرة واحدة هنا لا داخل التصميم، تماماً كما يفعل
     * {@code headerFactory} لمطبوعات JavaFX: التقرير الفرعي يُنفَّذ مرة لكل صفحة، وقراءةُ
     * الإعدادات داخله تعني استعلاماً لكل صفحة.</p>
     *
     * @param parameters خريطة معاملات التقرير - تُعدَّل ويُعاد نفسها للتسلسل
     */
    public Map<String, Object> withCenterHeader(Map<String, Object> parameters) {
        CenterSettings settings = settingsService.getSettings();

        parameters.put("HEADER_REPORT", compile("CenterHeader.jrxml"));
        parameters.put("SHOW_CENTER", PrintPreferences.printsCenterHeader());
        parameters.put("CENTER_NAME", settings != null && settings.getCenterName() != null
                && !settings.getCenterName().isBlank()
                ? settings.getCenterName()
                : I18n.get("report.header.defaultCenterName"));
        parameters.put("CENTER_PHONE", settings == null || settings.getCenterPhone() == null
                || settings.getCenterPhone().isBlank()
                ? null : I18n.format("report.header.phone", settings.getCenterPhone()));
        parameters.put("LOGO_PATH", existingLogoPath(settings));

        return parameters;
    }

    /**
     * ذيل الورقة الموحّد: تاريخ الطباعة ورقم الصفحة.
     *
     * <p>مفتاحان مشتركان لا مفتاحان لكل كشف: "الصفحة" و"تاريخ الطباعة" لا يختلفان من
     * تقرير إلى تقرير، ونسخُهما مع كل ملف تصميم جديد يعني ترجمةً تُراجَع في عشرة مواضع.</p>
     */
    public Map<String, Object> withSheetFooter(Map<String, Object> parameters) {
        parameters.put("PRINTED_AT", I18n.format("report.sheet.printedAt",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        parameters.put("PAGE_LABEL", I18n.get("report.sheet.page"));
        return parameters;
    }

    /**
     * مسار ملف الشعار إن كان موجوداً فعلاً، وإلا {@code null}.
     * الفحص هنا لا في التصميم: جاسبر يرمي على ملف غائب، والشعار الذي نقله أحدهم يجب
     * أن يعني ورقةً بلا شعار لا طباعةً تفشل.
     */
    private String existingLogoPath(CenterSettings settings) {
        if (settings == null || settings.getLogoPath() == null || settings.getLogoPath().isBlank()) {
            return null;
        }
        File logo = new File(settings.getLogoPath());
        return logo.isFile() ? logo.getAbsolutePath() : null;
    }

    private IllegalStateException generationFailed(JRException e) {
        return new IllegalStateException(I18n.format("error.report.generateFailed",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
    }

    private JasperPrint fillStudentEnrollments(String studentName, String studentDetails,
                                               List<MembershipRow> memberships) {
        String none = I18n.get("common.none");
        long active = memberships.stream().filter(MembershipRow::active).count();

        Map<String, Object> parameters = withCenterHeader(new java.util.HashMap<>());
        parameters.put("REPORT_TITLE", I18n.get("report.enrollments.title"));
        parameters.put("STUDENT_NAME", studentName);
        parameters.put("STUDENT_DETAILS", studentDetails);
        parameters.put("SUMMARY", I18n.format("report.enrollments.summary",
                memberships.size(), active, memberships.size() - active));
        parameters.put("COL_GROUP", I18n.get("student.col.group"));
        parameters.put("COL_JOINED", I18n.get("student.col.joined"));
        parameters.put("COL_LEFT", I18n.get("student.col.left"));
        parameters.put("COL_HELD", I18n.get("student.col.sessionsHeld"));
        parameters.put("COL_ATTENDED", I18n.get("student.col.sessionsAttended"));
        parameters.put("COL_RATE", I18n.get("student.col.attendanceRate"));
        parameters.put("NO_ROWS", I18n.get("report.enrollments.noRows"));
        withSheetFooter(parameters);

        List<EnrollmentReportRow> rows = memberships.stream()
                .map(row -> new EnrollmentReportRow(
                        row.groupName(),
                        String.valueOf(row.joinDate()),
                        row.active() ? I18n.get("student.membershipActive")
                                : (row.leaveDate() == null ? none : String.valueOf(row.leaveDate())),
                        String.valueOf(row.sessionsHeld()),
                        String.valueOf(row.sessionsAttended()),
                        row.attendanceRate() == null ? none : row.attendanceRate() + "%"))
                .toList();

        try {
            return JasperFillManager.fillReport(compile("StudentEnrollments.jrxml"), parameters,
                    new JRBeanCollectionDataSource(rows));
        } catch (JRException e) {
            throw generationFailed(e);
        }
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
                // عدد المشتركين بجانب الحاضرين: لا يدخل في المستحق، لكنه ما يجعل
                // رقم الحضور قابلاً للقراءة - "12 من 30" لا "12"
                document.add(new Label(I18n.format("report.teacher.row",
                        s.sessionDate(), s.groupName(), s.attendees(), s.enrolled(),
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
