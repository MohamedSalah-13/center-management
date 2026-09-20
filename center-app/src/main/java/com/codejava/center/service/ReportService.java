package com.codejava.center.service;

import com.codejava.center.core.print.DocumentKind;
import com.codejava.center.core.print.SheetHeaderPolicy;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.Transaction;
import com.codejava.center.service.dto.ArrearsReportRow;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceLogSheetRow;
import com.codejava.center.service.dto.AttendanceReportRow;
import com.codejava.center.service.dto.AttendanceSummary;
import com.codejava.center.service.dto.AuditReportRow;
import com.codejava.center.service.dto.DayScheduleRow;
import com.codejava.center.service.dto.EnrollmentReportRow;
import com.codejava.center.service.dto.ExpenseSheetRow;
import com.codejava.center.service.dto.GroupAttendanceReport;
import com.codejava.center.service.dto.GroupListRow;
import com.codejava.center.service.dto.GroupRosterRow;
import com.codejava.center.service.dto.IdCardRow;
import com.codejava.center.service.dto.MembershipRow;
import com.codejava.center.service.dto.Sheet;
import com.codejava.center.service.dto.ShiftMovementRow;
import com.codejava.center.service.dto.TeacherListRow;
import com.codejava.center.service.dto.TeacherSessionRow;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.service.dto.ShiftSummary;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.Durations;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.WeekDays;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * يملأ كشوف جاسبر. <b>ولا يسلّمها.</b>
 *
 * <p>كل دالة عامة هنا تعيد {@link Sheet} — ورقةً مملوءة ومعها نوعها وبادئة اسم ملفها —
 * ولا تعرف ما سيحلّ بها. كانت تطبع أو تكتب ملفاً مؤقتاً بعد الملء مباشرةً، فكانت تحمل
 * {@code javax.print} ومجلدَ الملفات المؤقتة وعارضَ الـ PDF معها إلى كل من يستدعيها؛
 * وخادمٌ يطلب نفس الورقة ليردّها في جواب HTTP لا طابعة له ولا مستخدمَ أمام شاشة.</p>
 *
 * <p>المسلِّم على Desktop هو {@code util/Sheets}: يقرأ تفضيل الجهاز فيطبع أو يكتب PDF،
 * أو يحفظ ملفاً باسم يبقى. والشاشة تجمع الاثنين في سطر واحد داخل {@code FxAsync}.</p>
 *
 * <p>ولا استعلام يُكتب داخل ملف تصميم: كل ورقة تُملأ من قائمة كائنات جاهزة
 * ({@code JRBeanCollectionDataSource}) تبنيها الخدمات، ولهذا لا {@code DataSource} في
 * هذا الصنف أصلاً. ما يُطبع هو ما كان على الشاشة، وسؤالُ قاعدة البيانات مرة أخرى يفتح
 * باب أن يختلف الاثنان.</p>
 */
@Service
public class ReportService {

    /**
     * ذاكرة مؤقتة للتقارير المترجَمة.
     * ترجمة ملف jrxml عملية ثقيلة، وكانت تتكرر مع كل استخراج أو معاينة
     * رغم أن ملفات التصميم لا تتغير أثناء التشغيل.
     */
    private final Map<String, JasperReport> compiledReports = new ConcurrentHashMap<>();

    private final SettingsService settingsService;

    /**
     * هل تُملأ الورقة بترويسة السنتر. واجهةٌ لا {@code PrintPreferences} مباشرةً: ذاك
     * يستورد {@code javafx.print} فكانت هذه الخدمة تجرّ الواجهة الرسومية خلفها إلى كل
     * مكان تُستدعى منه. راجع {@link SheetHeaderPolicy}.
     */
    private final SheetHeaderPolicy headerPolicy;

    public ReportService(SettingsService settingsService, SheetHeaderPolicy headerPolicy) {
        this.settingsService = settingsService;
        this.headerPolicy = headerPolicy;
    }

    /**
     * صيغة الوقت في ترويسات المطبوعات وذيولها، ومعها صيغةُ الساعة في أعمدة الحركات.
     *
     * <p>نصوصٌ لا ثوابت: {@code ofPattern} بلا لغة تقرأ {@link Locale#getDefault} - وهو
     * ما كان يصحّ ما دام برنامجُ سطح المكتب هو من يضبطه على لغة الواجهة. طبقةُ الأعمال
     * لم تعد تضبطه، وخادمٌ يملأ ورقةً لسنترٍ عربي بلغته هو يكتب "04:30 PM" في كشفٍ
     * عربي، أو أرقاماً هندية في كشفٍ كلُّ أرقامه لاتينية. وحقلٌ ساكن يُبنى مرة يجمّد
     * اللغة على أوّلِ من لمس الصنف.</p>
     */
    private static final String TIMESTAMP = "yyyy-MM-dd HH:mm";
    private static final String CLOCK = "hh:mm a";
    private static final String TIMESTAMP_SECONDS = "yyyy-MM-dd HH:mm:ss";

    /**
     * جرد الوردية: الملخّص ثم تفصيل الحركات، على رول الكاشير.
     *
     * <p>{@link DocumentKind#RECEIPT} لا {@code REPORT}: هذا هو المطبوع الذي يُقصّ عند
     * تقفيل الدرج ويُدبَّس على النقد المسلَّم، فمكانه الرول الذي أمام الكاشير - لا طابعة
     * A4 في غرفة أخرى. وكان يخرج A4 كاملة لأجل ملخّص من أربعة أسطر.</p>
     */
    public Sheet shiftSummarySheet(LocalDate day, ShiftSummary summary,
                                   List<Transaction> movements) {
        Map<String, Object> parameters = withReceiptHeader(new java.util.HashMap<>());
        parameters.put("REPORT_TITLE", I18n.format("report.shift.title", day));
        parameters.put("INCOME_LINE", summaryLine("shift.income", summary.totalIncome()));
        parameters.put("EXPENSES_LINE", summaryLine("shift.expenses", summary.totalExpense()));
        parameters.put("PAYOUTS_LINE", summaryLine("shift.payouts", summary.totalTeacherPayouts()));
        parameters.put("NET_LINE", summaryLine("shift.net", summary.net()));
        parameters.put("DETAILS_TITLE", I18n.format("report.shift.details", movements.size()));
        parameters.put("PRINTED_AT", I18n.format("report.sheet.printedAt",
                LocalDateTime.now().format(formatter(TIMESTAMP))));

        DateTimeFormatter clock = formatter(CLOCK);
        List<ShiftMovementRow> rows = movements.stream()
                .map(movement -> new ShiftMovementRow(
                        movement.getTransactionDate().format(clock),
                        MoneyUtils.format(movement.getAmount()),
                        movement.getDescription()))
                .toList();

        return sheet(fill("ShiftSummary.jrxml", parameters, rows), "shift_summary_",
                DocumentKind.RECEIPT);
    }

    /** تقرير المتأخرات: قائمة المدينين ومبالغهم مع بيانات التواصل */
    public Sheet arrearsSheet(List<StudentBalance> arrears,
                              java.math.BigDecimal totalDue) {
        String none = I18n.get("common.none");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.arrears.title"));
        parameters.put("SCOPE", I18n.format("report.arrears.scope", arrears.size()));
        parameters.put("TOTAL_LINE", I18n.format("report.arrears.total",
                arrears.size(), MoneyUtils.formatWithCurrency(totalDue)));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_NAME", I18n.get("arrears.col.name"));
        parameters.put("COL_BARCODE", I18n.get("arrears.col.barcode"));
        parameters.put("COL_PARENT", I18n.get("arrears.col.parentPhone"));
        parameters.put("COL_DUE", I18n.get("arrears.col.due"));
        parameters.put("NO_ROWS", I18n.get("report.arrears.noRows"));

        int[] serial = {0};
        List<ArrearsReportRow> rows = arrears.stream()
                .map(row -> new ArrearsReportRow(
                        String.valueOf(++serial[0]),
                        row.studentName(),
                        row.barcode() == null ? none : row.barcode(),
                        row.parentPhone() == null ? none : row.parentPhone(),
                        MoneyUtils.formatWithCurrency(row.amountDue())))
                .toList();

        return sheet(fill("ArrearsReport.jrxml", parameters, rows), "arrears_",
                DocumentKind.REPORT);
    }

    /**
     * إيصال استلام نقدية، على رول الإيصالات.
     *
     * <p>كان يُبنى داخل شاشة الخزينة بترويسة نصية ثابتة لا تحمل اسم السنتر ولا شعاره رغم
     * أن الإعدادات تجمعهما، ثم صار مطبوعة JavaFX، وهو الآن ورقة جاسبر كبقية المطبوعات.</p>
     */
    public Sheet paymentReceiptSheet(String studentName, String groupName,
                                     java.math.BigDecimal amount,
                                     java.math.BigDecimal newBalance, String description) {
        Map<String, Object> parameters = withReceiptHeader(new java.util.HashMap<>());
        parameters.put("RECEIPT_TITLE", I18n.get("report.receipt.title"));
        parameters.put("DATE_LINE", I18n.format("report.receipt.date",
                LocalDateTime.now().format(formatter(TIMESTAMP))));
        parameters.put("STUDENT_LINE", I18n.format("report.receipt.student", studentName));
        parameters.put("GROUP_LINE", I18n.format("report.receipt.group", groupName));
        parameters.put("DESCRIPTION_LINE", I18n.format("report.receipt.description", description));
        parameters.put("AMOUNT_LINE", I18n.format("report.receipt.amount",
                MoneyUtils.formatWithCurrency(amount)));
        parameters.put("BALANCE_LINE", I18n.format("report.receipt.balance",
                MoneyUtils.formatWithCurrency(newBalance)));

        // سجلّ واحد لا صفر: الإيصال بلا صفوف تفصيل، وفرقة detail هي ما يحمل نصّه
        return sheet(fill("PaymentReceipt.jrxml", parameters, List.of(new Object())),
                "receipt_", DocumentKind.RECEIPT);
    }

    /** تقرير حضور وغياب مجموعة خلال فترة */
    public Sheet attendanceReportSheet(GroupAttendanceReport report,
                                       LocalDate from, LocalDate to) {
        String none = I18n.get("common.none");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.format("report.attendance.title", report.groupName()));
        parameters.put("PERIOD", I18n.format("report.attendance.period",
                from, to, report.totalSessions()));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_NAME", I18n.get("attReport.col.name"));
        parameters.put("COL_BARCODE", I18n.get("attReport.col.barcode"));
        parameters.put("COL_PARENT", I18n.get("attReport.col.parentPhone"));
        parameters.put("COL_ATTENDED", I18n.get("attReport.col.attended"));
        parameters.put("COL_ABSENT", I18n.get("attReport.col.absent"));
        parameters.put("COL_RATE", I18n.get("attReport.col.rate"));
        parameters.put("NO_ROWS", I18n.get("report.attendance.noRows"));

        int[] serial = {0};
        List<AttendanceReportRow> rows = report.rows().stream()
                .map(row -> new AttendanceReportRow(
                        String.valueOf(++serial[0]),
                        row.studentName(),
                        row.barcode() == null ? none : row.barcode(),
                        row.parentPhone() == null ? none : row.parentPhone(),
                        String.valueOf(row.attended()),
                        String.valueOf(Math.max(0, report.totalSessions() - row.attended())),
                        report.totalSessions() == 0
                                ? none
                                : Math.round((row.attended() * 100.0) / report.totalSessions()) + "%"))
                .toList();

        return sheet(fill("AttendanceReport.jrxml", parameters, rows), "attendance_",
                DocumentKind.REPORT);
    }

    /**
     * تقرير المصروفات خلال فترة: بندٌ لكل مصروف، وإجماليها في آخر الورقة.
     *
     * <p>الإجمالي يصل وسيطاً من الشاشة لا يُحسب هنا، كما في {@link #arrearsSheet}:
     * الرقم المطبوع هو الرقم الذي رآه المستخدم قبل أن يضغط الطباعة، وحسابُه مرة ثانية
     * يفتح باب أن يخالف الورقةُ الشاشةَ - وورقة مصروفات تخالف ما على الشاشة تُفقد الثقة
     * في الاثنتين معاً.</p>
     *
     * <p>ووصف التصفية يُطبع ويتكرّر في رأس كل صفحة: ورقةٌ تُقرأ بعد شهرين بلا سطر يقول
     * "من كذا إلى كذا" تُقرأ على أنها مصروفات السنتر كلها.</p>
     */
    public Sheet expenseReportSheet(List<Transaction> expenses,
                                    java.math.BigDecimal total, String filterDescription) {
        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.expenses.title"));
        parameters.put("SCOPE", I18n.format("report.expenses.scope", filterDescription, expenses.size()));
        parameters.put("TOTAL_LINE", I18n.format("report.expenses.total",
                expenses.size(), MoneyUtils.formatWithCurrency(total)));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_DATE", I18n.get("expenseReport.col.date"));
        parameters.put("COL_TIME", I18n.get("expenseReport.col.time"));
        parameters.put("COL_DESCRIPTION", I18n.get("expenseReport.col.description"));
        parameters.put("COL_AMOUNT", I18n.get("expenseReport.col.amount"));
        parameters.put("NO_ROWS", I18n.get("report.expenses.noRows"));

        DateTimeFormatter clock = formatter(CLOCK);
        int[] serial = {0};
        List<ExpenseSheetRow> rows = expenses.stream()
                .map(expense -> new ExpenseSheetRow(
                        String.valueOf(++serial[0]),
                        String.valueOf(expense.getTransactionDate().toLocalDate()),
                        expense.getTransactionDate().format(clock),
                        expense.getDescription(),
                        MoneyUtils.format(expense.getAmount())))
                .toList();

        return sheet(fill("ExpenseReport.jrxml", parameters, rows), "expenses_",
                DocumentKind.REPORT);
    }

    /**
     * كشف الحضور والانصراف كما تعرضه الشاشة بعد التصفية: سطرٌ لكل مرة دخل فيها طالب.
     *
     * <p>وصف التصفية يُطبع ويتكرّر في رأس كل صفحة، كما في كشف المجموعات: ورقةٌ تُلتقط من
     * الطابعة بعد يومين بلا سطرٍ يقول "من كذا إلى كذا، مجموعة كذا" تُقرأ على أنها حضور
     * السنتر كله.</p>
     *
     * <p>الأوقات والمدد تصل نصّاً جاهزاً من {@code Durations} و{@code I18n}: هي بعينها ما
     * كان على الشاشة، فلا يقرأ الموظف رقماً على الورقة يخالف ما رآه قبل لحظة.</p>
     */
    public Sheet attendanceLogSheet(List<AttendanceLogRow> log, String filterDescription) {
        String noTime = I18n.get("common.empty");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.attendanceLog.title"));
        parameters.put("SCOPE", I18n.format("report.attendanceLog.scope", filterDescription, log.size()));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_NAME", I18n.get("attLog.col.name"));
        parameters.put("COL_GROUP", I18n.get("attLog.col.group"));
        parameters.put("COL_DATE", I18n.get("attLog.col.date"));
        parameters.put("COL_TIME_IN", I18n.get("attLog.col.timeIn"));
        parameters.put("COL_TIME_OUT", I18n.get("attLog.col.timeOut"));
        parameters.put("COL_DURATION", I18n.get("attLog.col.duration"));
        parameters.put("COL_STATE", I18n.get("attLog.col.state"));
        parameters.put("NO_ROWS", I18n.get("report.attendanceLog.noRows"));

        DateTimeFormatter clock = formatter(CLOCK);
        int[] serial = {0};
        List<AttendanceLogSheetRow> rows = log.stream()
                .map(row -> new AttendanceLogSheetRow(
                        String.valueOf(++serial[0]),
                        row.studentName(),
                        row.groupName(),
                        String.valueOf(row.sessionDate()),
                        row.timeIn() == null ? noTime : row.timeIn().format(clock),
                        row.timeOut() == null ? noTime : row.timeOut().format(clock),
                        Durations.format(row.duration()),
                        row.state().getDisplayName()))
                .toList();

        return sheet(fill("AttendanceLogSheet.jrxml", parameters, rows), "attendance_log_",
                DocumentKind.REPORT);
    }

    /**
     * كشف المجموعات كما تعرضها الشاشة بعد التصفية.
     *
     * <p>وصف التصفية يُطبع في أعلى الورقة: كشف يقول "مجموعات المعلم فلان يوم السبت"
     * يُقرأ بعد شهر، وكشف بلا وصف يبدو أنه كل مجموعات السنتر وليس كذلك.</p>
     */
    public Sheet groupsListSheet(List<CourseGroup> groups, Map<Long, Long> memberCounts,
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

        return sheet(fill("GroupsList.jrxml", parameters, rows), "groups_list_", DocumentKind.REPORT);
    }

    /**
     * جدول حصص يوم واحد كما تعرضه الشاشة.
     *
     * <p>ورقة تُعلَّق على المكتب أو تُسلَّم لمن يفتح السنتر، ولذلك يُطبع اليوم وملخّصه في
     * رأس كل صفحة: جدولٌ بلا يومه يبدو جدول اليوم الذي وُجد فيه، وهو أسوأ من ورقة غائبة
     * لأنه يُصدَّق.</p>
     *
     * <p>الصفوف تصل جاهزة من {@code DayScheduleService}: هي بعينها صفوف الجدول على
     * الشاشة، فالورقة نسخة ممّا كان أمام الموظف لا حساب ثانٍ قد يخالفه.</p>
     */
    public Sheet dayScheduleSheet(LocalDate date, List<DayScheduleRow> rows, String summary) {
        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.daySchedule.title"));
        parameters.put("SCOPE", I18n.format("report.daySchedule.scope",
                WeekDays.displayName(date.getDayOfWeek()), date, summary));
        parameters.put("COL_GROUP", I18n.get("daySchedule.col.group"));
        parameters.put("COL_TEACHER", I18n.get("daySchedule.col.teacher"));
        parameters.put("COL_LEVEL", I18n.get("daySchedule.col.level"));
        parameters.put("COL_TIME", I18n.get("daySchedule.col.time"));
        parameters.put("COL_STATUS", I18n.get("daySchedule.col.status"));
        parameters.put("COL_STARTED", I18n.get("daySchedule.col.started"));
        parameters.put("COL_ENDED", I18n.get("daySchedule.col.ended"));
        parameters.put("COL_ATTENDANCE", I18n.get("daySchedule.col.attendance"));
        parameters.put("NO_ROWS", I18n.get("daySchedule.noRows"));

        return sheet(fill("DaySchedule.jrxml", parameters, rows), "day_schedule_", DocumentKind.REPORT);
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
    public Sheet auditReportSheet(List<com.codejava.center.domain.AuditLog> events,
                                  LocalDate from, LocalDate to) {
        String none = I18n.get("common.none");

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.audit.title"));
        parameters.put("PERIOD", I18n.format("report.audit.period", from, to, events.size()));
        parameters.put("COL_TIME", I18n.get("audit.col.time"));
        parameters.put("COL_ACTOR", I18n.get("audit.col.actor"));
        parameters.put("COL_ACTION", I18n.get("audit.col.action"));
        parameters.put("COL_TARGET", I18n.get("audit.col.target"));
        parameters.put("COL_AMOUNT", I18n.get("audit.col.amount"));
        parameters.put("COL_STATUS", I18n.get("audit.col.status"));
        parameters.put("NO_ROWS", I18n.get("report.audit.noRows"));

        DateTimeFormatter seconds = formatter(TIMESTAMP_SECONDS);
        List<AuditReportRow> rows = events.stream()
                .map(event -> new AuditReportRow(
                        event.getOccurredAt().format(seconds),
                        // اسم المستخدم فارغ يعني النظام لا مجهولاً: النسخة المجدولة تجري
                        // على خيط بلا جلسة، ونسبتها إلى آخر من دخل كذبة
                        event.getActorUsername() == null
                                ? I18n.get("audit.systemActor") : event.getActorUsername(),
                        event.getAction().getDisplayName(),
                        event.getEntityLabel() == null ? none : event.getEntityLabel(),
                        event.getAmount() == null ? none : MoneyUtils.format(event.getAmount()),
                        I18n.get(event.isSuccessful() ? "audit.status.ok" : "audit.status.failed"),
                        event.getDetails() == null || event.getDetails().isBlank()
                                ? null : I18n.format("report.audit.details", event.getDetails())))
                .toList();

        return sheet(fill("AuditReport.jrxml", parameters, rows), "audit_", DocumentKind.REPORT);
    }

    /**
     * كشف المعلمين كما تعرضهم الشاشة بعد التصفية.
     *
     * <p>وصف التصفية يُطبع في أعلى الورقة ويتكرّر في رأس كل صفحة، لنفس سبب
     * {@link #groupsListSheet}: كشفٌ يقول "المادة: رياضيات - نوع العمولة: نسبة مئوية"
     * يُقرأ بعد شهر، وكشفٌ بلا وصف يبدو أنه كل معلمي السنتر وليس كذلك.</p>
     *
     * <p>وقيمة العمولة تُكتب بلا رمز عملة: هي نسبة مئوية في اتفاق النسبة ومبلغٌ في اتفاق
     * المبلغ الثابت والإيجار، وإلحاق العملة بها يجعل "50" تُقرأ خمسين جنيهاً وهي خمسون
     * في المئة - وهو الفرق بين حصة معلم وحصة السنتر كلها.</p>
     */
    public Sheet teachersListSheet(List<Teacher> teachers, String filterDescription) {
        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.teachers.title"));
        parameters.put("SCOPE", I18n.format("report.teachers.scope", filterDescription, teachers.size()));
        parameters.put("COL_SERIAL", I18n.get("report.group.col.serial"));
        parameters.put("COL_NAME", I18n.get("teacher.col.name"));
        parameters.put("COL_SUBJECT", I18n.get("teacher.col.subject"));
        parameters.put("COL_TYPE", I18n.get("teacher.col.type"));
        parameters.put("COL_VALUE", I18n.get("teacher.col.value"));
        parameters.put("NO_ROWS", I18n.get("report.teachers.noTeachers"));

        int[] serial = {0};
        List<TeacherListRow> rows = teachers.stream()
                .map(teacher -> new TeacherListRow(
                        String.valueOf(++serial[0]),
                        teacher.getName(),
                        teacher.getSubject(),
                        CommissionTypes.displayName(teacher.getCommissionType()),
                        MoneyUtils.format(teacher.getCommissionValue())))
                .toList();

        return sheet(fill("TeachersList.jrxml", parameters, rows), "teachers_list_",
                DocumentKind.REPORT);
    }

    /** سطر "بند: مبلغ" في الملخّصات، بنصّ عنوانه مترجَماً وعملة السنتر مذيَّلة به */
    private String summaryLine(String labelKey, java.math.BigDecimal value) {
        return I18n.format("report.summaryLine", I18n.get(labelKey), MoneyUtils.formatWithCurrency(value));
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
    public Sheet groupRosterSheet(CourseGroup group, List<MembershipRow> members) {
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

        return sheet(fill("GroupStudents.jrxml", parameters, rows), "group_roster_", DocumentKind.REPORT);
    }

    /**
     * يبني ورقة جاسبر من قائمة كائنات.
     * الملء وحده هنا، والتسليم عند من يملك جهازاً: {@code util/Sheets} على Desktop.
     */
    private JasperPrint fill(String template, Map<String, Object> parameters, List<?> rows) {
        try {
            return JasperFillManager.fillReport(compile(template), parameters,
                    new JRBeanCollectionDataSource(rows));
        } catch (JRException e) {
            throw Sheet.generationFailed(e);
        }
    }

    /**
     * يغلّف الورقة المملوءة بما يحتاجه من يسلّمها: نوعها وبادئة اسم ملفها.
     *
     * <p>هنا ينتهي عمل هذه الخدمة. كانت تطبع أو تكتب ملفاً مؤقتاً بعد الملء مباشرةً،
     * فكانت تحمل {@code javax.print} ومجلد الملفات المؤقتة إلى كل من يستدعيها — وخادمٌ
     * يطلب نفس الورقة ليردّها في جواب HTTP لا طابعة له ولا مستخدمَ أمام شاشة يفتح له
     * ملفاً. راجع {@link Sheet}.</p>
     */
    private Sheet sheet(JasperPrint print, String fileNamePrefix, DocumentKind kind) {
        return new Sheet(print, kind, fileNamePrefix);
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
     * <p>وهي الورقة الوحيدة التي <b>تُحفظ</b> لا تُطبع الآن: تُقصّ على ورق كارنيهات بعد
     * حين، فمكانها ملفٌ باسم يعرفه صاحبه لا ملفٌ مؤقت. والفرق كلّه عند المسلِّم
     * ({@code Sheets.save} بدل {@code Sheets.deliver})؛ الملء واحد.</p>
     */
    public Sheet studentIdCardsSheet(List<Student> students) {
        Map<String, Object> parameters = withCenterHeader(new java.util.HashMap<>());
        parameters.put("CARD_TITLE", I18n.get("report.idCards.cardTitle"));

        List<IdCardRow> cards = students.stream()
                .map(student -> new IdCardRow(
                        student.getName(),
                        student.getBarcode(),
                        student.getSchoolLevel() == null ? null : student.getSchoolLevel().getDisplayName()))
                .toList();

        return sheet(fill("StudentIdCards.jrxml", parameters, cards), "student_id_cards_",
                DocumentKind.REPORT);
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
    public Sheet studentEnrollmentsSheet(String studentName, String studentDetails,
                                         List<MembershipRow> memberships) {
        return sheet(fillStudentEnrollments(studentName, studentDetails, memberships),
                "student_enrollments_", DocumentKind.REPORT);
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
        parameters.put("SHOW_CENTER", headerPolicy.printsCenterHeader());
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
     * نفس الترويسة، بتصميم الرول: شعارٌ فوق ثم الاسم فالهاتف، كلها في الوسط.
     *
     * <p>ملف ثانٍ لا معامل عرض: التقرير الفرعي في جاسبر ثابت العرض، ورول 80mm لا يتّسع
     * لشعار بجوار اسم. الترتيب رأسيّ هناك وأفقيّ هنا، والمعاملات هي هي.</p>
     */
    public Map<String, Object> withReceiptHeader(Map<String, Object> parameters) {
        withCenterHeader(parameters);
        parameters.put("HEADER_REPORT", compile("ReceiptHeader.jrxml"));
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
                LocalDateTime.now().format(formatter(TIMESTAMP))));
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
            throw Sheet.generationFailed(e);
        }
    }

    /**
     * كشف حساب معلم يحتوي تفصيل الحصص فعلياً.
     * كان يطبع سطراً واحداً نصه "تفاصيل الحصص المالية ستدرج هنا لاحقاً".
     */
    public Sheet teacherStatementSheet(Teacher teacher, List<SessionPayout> sessions) {
        java.math.BigDecimal total = sessions.stream()
                .map(SessionPayout::payoutAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> parameters = withSheetFooter(withCenterHeader(new java.util.HashMap<>()));
        parameters.put("REPORT_TITLE", I18n.get("report.teacher.title"));
        parameters.put("TEACHER_INFO", I18n.format("report.teacher.info",
                teacher.getName(), teacher.getSubject(),
                CommissionTypes.displayName(teacher.getCommissionType()),
                MoneyUtils.format(teacher.getCommissionValue())));
        parameters.put("TOTAL_LINE", I18n.format("report.teacher.total",
                MoneyUtils.formatWithCurrency(total)));
        parameters.put("COL_DATE", I18n.get("payout.col.date"));
        parameters.put("COL_GROUP", I18n.get("payout.col.group"));
        parameters.put("COL_ATTENDANCE", I18n.get("payout.col.attendees"));
        parameters.put("COL_REVENUE", I18n.get("payout.col.revenue"));
        parameters.put("COL_PAYOUT", I18n.get("payout.col.payout"));
        parameters.put("NO_ROWS", I18n.get("report.teacher.noSessions"));

        List<TeacherSessionRow> rows = sessions.stream()
                .map(session -> new TeacherSessionRow(
                        String.valueOf(session.sessionDate()),
                        session.groupName(),
                        // عدد المشتركين بجانب الحاضرين: لا يدخل في المستحق، لكنه ما يجعل
                        // رقم الحضور قابلاً للقراءة - "12 / 30" لا "12"
                        I18n.format("group.membersOf", session.attendees(), session.enrolled()),
                        MoneyUtils.format(session.totalRevenue()),
                        MoneyUtils.format(session.payoutAmount())))
                .toList();

        return sheet(fill("TeacherStatement.jrxml", parameters, rows), "teacher_statement_",
                DocumentKind.REPORT);
    }

    /** صيغةُ تاريخٍ بلغة من سيقرأ الورقة */
    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, I18n.current());
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
}
