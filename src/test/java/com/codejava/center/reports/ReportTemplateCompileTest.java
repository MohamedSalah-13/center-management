package com.codejava.center.reports;

import com.codejava.center.service.dto.ArrearsReportRow;
import com.codejava.center.service.dto.AttendanceReportRow;
import com.codejava.center.service.dto.AuditReportRow;
import com.codejava.center.service.dto.DayScheduleRow;
import com.codejava.center.service.dto.EnrollmentReportRow;
import com.codejava.center.service.dto.ShiftMovementRow;
import com.codejava.center.service.dto.TeacherListRow;
import com.codejava.center.service.dto.TeacherSessionRow;
import com.codejava.center.service.dto.GroupListRow;
import com.codejava.center.service.dto.IdCardRow;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import com.codejava.center.service.dto.GroupRosterRow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حارس ملفات التصميم.
 *
 * <p>ملف jrxml يُترجَم وقت التشغيل لا وقت البناء، تماماً كـ JPQL وكشاشات FXML: تعبير خاطئ
 * أو حقل باسم لا وجود له يمرّ من البناء كله ومن بقية الاختبارات، ولا يظهر إلا لحظة ضغط
 * المستخدم زرّ الطباعة عند العميل - وهي أسوأ لحظة يظهر فيها.</p>
 *
 * <p>الترجمة وحدها هي المفحوصة هنا لا الملء: الملء يحتاج بيانات واتصالاً بقاعدة، والخطأ
 * الذي يقع فعلاً في هذه الملفات خطأ بنية لا خطأ بيانات.</p>
 */
class ReportTemplateCompileTest {

    private static final Path REPORTS_DIR = Path.of("src/main/resources/reports");

    @Test
    void everyReportTemplateCompiles() throws Exception {
        List<String> broken = new ArrayList<>();
        List<Path> templates = templates();

        // مجلد فارغ يجعل الاختبار ينجح بلا أن يفحص شيئاً، وهو أسوأ من غيابه
        assertThat(templates).as("ملفات التصميم في " + REPORTS_DIR).isNotEmpty();

        for (Path template : templates) {
            try (InputStream stream = Files.newInputStream(template)) {
                JasperCompileManager.compileReport(stream);
            } catch (Exception e) {
                broken.add(template.getFileName() + " -> " + e.getMessage());
            }
        }

        assertThat(broken).as("ملفات تصميم لا تُترجَم، وتفشل عند أول طباعة").isEmpty();
    }

    /**
     * كشف الاشتراكات يُملأ ويخرج PDF فعلاً.
     *
     * <p>الترجمة وحدها لا تكشف الفخّ الحقيقي هنا: جاسبر يقرأ حقول الصف بأسلوب
     * {@code getGroupName()}، فلو صار {@link EnrollmentReportRow} سجلّاً يوماً خرجت
     * الورقة بأعمدة فارغة بلا خطأ واحد. الملء هو ما يكشف ذلك، وهو أيضاً ما يتحقّق من
     * أن خطّ العربية المسجَّل في {@code fonts.xml} ما زال يُعثر عليه.</p>
     */
    @Test
    void enrollmentsSheetFillsAndExportsWithItsValues() throws Exception {
        JasperPrint print = fillEnrollments(true);

        assertThat(print.getPages()).as("صفحات الكشف").isNotEmpty();

        // القيم تصل الورقة فعلاً: عمود فارغ لا يرفع استثناءً، فيُفحص محتوى النصّ نفسه
        assertThat(JasperExportManager.exportReportToXml(print))
                .contains("مجموعة الأحد")
                .contains("83%")
                .contains("COL_GROUP");
    }

    /**
     * ترويسة السنتر تصل الورقة عبر التقرير الفرعي، وتغيب حين تُطفأ.
     *
     * <p>التقرير الفرعي حلقة تُوصَل وقت التشغيل: مُعامل غير مُمرَّر أو {@code dataSource}
     * منسيّ يعني ترويسةً غائبة عن كل ورقة بلا خطأ واحد. والغياب حين تُطفأ يُفحص كذلك، لأن
     * شرطاً مكتوباً على العنصر بدل الفرقة يُخفي الترويسة ويُبقي فراغها.</p>
     */
    @Test
    void centerHeaderIsPrintedThroughItsSubreportAndCanBeTurnedOff() throws Exception {
        assertThat(JasperExportManager.exportReportToXml(fillEnrollments(true)))
                .as("الترويسة معروضة").contains("CENTER_NAME");

        assertThat(JasperExportManager.exportReportToXml(fillEnrollments(false)))
                .as("الترويسة مُطفأة").doesNotContain("CENTER_NAME");
    }

    /**
     * الكارنيهات تُملأ من {@link IdCardRow} لا من الكيان.
     *
     * <p>كان التصميم يعلن المرحلة نصاً بينما هي {@code enum} في {@code Student}، فيسقط
     * التصدير عند أول كارنيه. الملء هنا هو ما يمسك عودةَ ذلك: تمرير الكيان مباشرةً يبدو
     * في الكود اختصاراً بريئاً.</p>
     */
    @Test
    void idCardsFillWithTheirHeaderAndValues() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CARD_TITLE", "CARD_TITLE");
        parameters.put("CENTER_NAME", "CENTER_NAME");
        parameters.put("CENTER_PHONE", "CENTER_PHONE");
        parameters.put("SHOW_CENTER", true);
        parameters.put("LOGO_PATH", null);
        parameters.put("HEADER_REPORT", compiled("CenterHeader.jrxml"));

        List<IdCardRow> cards = List.of(
                new IdCardRow("أحمد محمود", "100234", "الصف الثالث الثانوي"),
                new IdCardRow("Sara Ali", "100235", null));

        JasperPrint print = JasperFillManager.fillReport(compiled("StudentIdCards.jrxml"), parameters,
                new JRBeanCollectionDataSource(cards));

        assertThat(JasperExportManager.exportReportToXml(print))
                .contains("CENTER_NAME")
                .contains("CARD_TITLE")
                .contains("أحمد محمود")
                .contains("الصف الثالث الثانوي");
    }

    /** كشف المجموعة: ترويسته، وسطر بيانات المجموعة، وصفوفه، وورقته حين لا مشترك */
    @Test
    void groupRosterSheetFillsWithItsHeaderAndSaysWhenEmpty() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        for (String key : new String[]{"CENTER_NAME", "CENTER_PHONE", "REPORT_TITLE", "GROUP_INFO",
                "COL_SERIAL", "COL_NAME", "COL_BARCODE", "COL_PARENT", "COL_JOINED",
                "COL_ATTENDANCE", "COL_RATE", "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"}) {
            parameters.put(key, key);
        }
        parameters.put("SHOW_CENTER", true);
        parameters.put("LOGO_PATH", null);
        parameters.put("HEADER_REPORT", compiled("CenterHeader.jrxml"));

        List<GroupRosterRow> rows = List.of(
                new GroupRosterRow("1", "أحمد محمود", "100234", "01000000000", "2025-09-10", "22 / 24", "92%"),
                new GroupRosterRow("2", "Sara Ali", "100235", "---", "2025-10-01", "0 / 4", "0%"));

        JasperReport report = compiled("GroupStudents.jrxml");

        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(rows))))
                .contains("CENTER_NAME")
                .contains("GROUP_INFO")
                .contains("COL_BARCODE")
                .contains("أحمد محمود")
                .contains("22 / 24")
                .contains("92%");

        // بلا مشتركين: ورقة تقول ذلك، لا ورقة فارغة تُقرأ كعطل في الطباعة
        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(List.of()))))
                .contains("NO_ROWS");
    }

    /** كشف المجموعات: ترويسته، ووصف التصفية، وصفوفه، وورقته حين لا تطابق التصفية شيئاً */
    @Test
    void groupsListSheetFillsWithItsHeaderAndSaysWhenEmpty() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        for (String key : new String[]{"CENTER_NAME", "CENTER_PHONE", "REPORT_TITLE", "SCOPE",
                "COL_NAME", "COL_TEACHER", "COL_LEVEL", "COL_DAYS", "COL_TIME", "COL_MEMBERS",
                "COL_PRICE", "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"}) {
            parameters.put(key, key);
        }
        parameters.put("SHOW_CENTER", true);
        parameters.put("LOGO_PATH", null);
        parameters.put("HEADER_REPORT", compiled("CenterHeader.jrxml"));

        List<GroupListRow> rows = List.of(
                new GroupListRow("٣ث - أ. سامي", "سامي عبد الله", "الصف الثالث الثانوي",
                        "السبت، الثلاثاء", "16:00 - 18:00", "5 / 20", "75.00"),
                new GroupListRow("٢ث - أ. هدى", "هدى كمال", "الصف الثاني الثانوي",
                        "الأحد", "18:00 - 20:00", "12 / 15", "60.00"));

        JasperReport report = compiled("GroupsList.jrxml");

        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(rows))))
                .contains("CENTER_NAME")
                .contains("SCOPE")
                .contains("COL_TEACHER")
                .contains("سامي عبد الله")
                .contains("5 / 20")
                .contains("75.00");

        // تصفية لا تطابق شيئاً: ورقة تقول ذلك ومعها وصف التصفية، لا ورقة فارغة
        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(List.of()))))
                .contains("NO_ROWS")
                .contains("SCOPE");
    }

    /** كشف المعلمين: ترويسته، ووصف التصفية، وصفوفه، وورقته حين لا تطابق التصفية أحداً */
    @Test
    void teachersListSheetFillsWithItsHeaderAndSaysWhenEmpty() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        for (String key : new String[]{"CENTER_NAME", "CENTER_PHONE", "REPORT_TITLE", "SCOPE",
                "COL_SERIAL", "COL_NAME", "COL_SUBJECT", "COL_TYPE", "COL_VALUE",
                "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"}) {
            parameters.put(key, key);
        }
        parameters.put("SHOW_CENTER", true);
        parameters.put("LOGO_PATH", null);
        parameters.put("HEADER_REPORT", compiled("CenterHeader.jrxml"));

        List<TeacherListRow> rows = List.of(
                new TeacherListRow("1", "سامي عبد الله", "رياضيات", "نسبة مئوية", "50.00"),
                new TeacherListRow("2", "Huda Kamal", "Physics", "إيجار قاعة", "200.00"));

        JasperReport report = compiled("TeachersList.jrxml");

        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(rows))))
                .contains("CENTER_NAME")
                .contains("SCOPE")
                .contains("COL_SUBJECT")
                .contains("سامي عبد الله")
                .contains("رياضيات")
                .contains("نسبة مئوية")
                .contains("50.00");

        // تصفية لا تطابق أحداً: ورقة تقول ذلك ومعها وصف التصفية، لا ورقة فارغة
        assertThat(JasperExportManager.exportReportToXml(
                JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(List.of()))))
                .contains("NO_ROWS")
                .contains("SCOPE");
    }

    /**
     * كل ورقة من الأوراق الستّ الباقية تُملأ، وقيمها تصل إليها.
     *
     * <p>مكتوبة جدولاً لا ستّ دوال متشابهة: ما يُفحص واحد - أن المعاملات وصلت، وأن الصفوف
     * ظهرت، وأن التقرير الفرعي وُصِل - والفرق بينها أسماء وأعمدة. ودالة لكل ورقة كانت
     * ستعني ستّ نسخ من نفس ثلاثة الأسطر، تُنسى إحداها عند إضافة ورقة سابعة.</p>
     */
    @Test
    void everySheetFillsWithItsParametersAndRows() throws Exception {
        record Sheet(String template, List<String> params, List<?> rows, String probe) {
        }

        List<Sheet> sheets = List.of(
                new Sheet("PaymentReceipt.jrxml",
                        List.of("RECEIPT_TITLE", "DATE_LINE", "STUDENT_LINE", "GROUP_LINE",
                                "DESCRIPTION_LINE", "AMOUNT_LINE", "BALANCE_LINE"),
                        List.of(new Object()), "AMOUNT_LINE"),

                new Sheet("ShiftSummary.jrxml",
                        List.of("REPORT_TITLE", "INCOME_LINE", "EXPENSES_LINE", "PAYOUTS_LINE",
                                "NET_LINE", "DETAILS_TITLE", "PRINTED_AT"),
                        List.of(new ShiftMovementRow("09:15 ص", "150.00", "رسوم اشتراك")), "رسوم اشتراك"),

                new Sheet("TeacherStatement.jrxml",
                        List.of("REPORT_TITLE", "TEACHER_INFO", "TOTAL_LINE", "COL_DATE", "COL_GROUP",
                                "COL_ATTENDANCE", "COL_REVENUE", "COL_PAYOUT", "PRINTED_AT",
                                "PAGE_LABEL", "NO_ROWS"),
                        List.of(new TeacherSessionRow("2026-01-05", "٣ث - أ. سامي", "12 / 30",
                                "900.00", "450.00")), "٣ث - أ. سامي"),

                new Sheet("ArrearsReport.jrxml",
                        List.of("REPORT_TITLE", "SCOPE", "TOTAL_LINE", "COL_SERIAL", "COL_NAME",
                                "COL_BARCODE", "COL_PARENT", "COL_DUE", "PRINTED_AT",
                                "PAGE_LABEL", "NO_ROWS"),
                        List.of(new ArrearsReportRow("1", "أحمد محمود", "100234",
                                "01222222222", "300.00 EGP")), "أحمد محمود"),

                new Sheet("AttendanceReport.jrxml",
                        List.of("REPORT_TITLE", "PERIOD", "COL_SERIAL", "COL_NAME", "COL_BARCODE",
                                "COL_PARENT", "COL_ATTENDED", "COL_ABSENT", "COL_RATE",
                                "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"),
                        List.of(new AttendanceReportRow("1", "سارة علي", "100235", "01333333333",
                                "10", "2", "83%")), "83%"),

                new Sheet("DaySchedule.jrxml",
                        List.of("REPORT_TITLE", "SCOPE", "COL_GROUP", "COL_TEACHER", "COL_LEVEL",
                                "COL_TIME", "COL_STATUS", "COL_STARTED", "COL_ENDED",
                                "COL_ATTENDANCE", "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"),
                        List.of(new DayScheduleRow("٣ث - أ. سامي", "سامي عبد الله",
                                "الصف الثالث الثانوي", "04:00 م - 06:00 م", "جارية الآن",
                                "16:05", "—", "12 / 20", DayScheduleRow.State.OPEN)), "16:05"),

                new Sheet("AuditReport.jrxml",
                        List.of("REPORT_TITLE", "PERIOD", "COL_TIME", "COL_ACTOR", "COL_ACTION",
                                "COL_TARGET", "COL_AMOUNT", "COL_STATUS", "PRINTED_AT",
                                "PAGE_LABEL", "NO_ROWS"),
                        List.of(new AuditReportRow("2026-08-06 14:30:00", "admin", "تسجيل دفعة",
                                "أحمد محمود", "300.00", "نجح", "required=ADMIN")), "required=ADMIN"));

        for (Sheet sheet : sheets) {
            Map<String, Object> parameters = new HashMap<>();
            for (String key : sheet.params()) {
                parameters.put(key, key);
            }
            parameters.put("CENTER_NAME", "CENTER_NAME");
            parameters.put("CENTER_PHONE", "CENTER_PHONE");
            parameters.put("SHOW_CENTER", true);
            parameters.put("LOGO_PATH", null);
            parameters.put("HEADER_REPORT", compiled(
                    sheet.template().equals("PaymentReceipt.jrxml")
                            || sheet.template().equals("ShiftSummary.jrxml")
                            ? "ReceiptHeader.jrxml" : "CenterHeader.jrxml"));

            String xml = JasperExportManager.exportReportToXml(JasperFillManager.fillReport(
                    compiled(sheet.template()), parameters,
                    new JRBeanCollectionDataSource(sheet.rows())));

            assertThat(xml).as(sheet.template()).contains("CENTER_NAME").contains(sheet.probe());
            // كل معامل بلغ الورقة فعلاً، لا العيّنة وحدها: معامل يُعلَن في التصميم
            // ولا يُمرَّر من الخدمة يخرج خانةً فارغة بلا خطأ. وNO_ROWS مستثنى لأنه
            // نصّ فرقة noData ولا يُطبع ما دام هناك صفوف
            for (String key : sheet.params()) {
                if (!key.equals("NO_ROWS")) {
                    assertThat(xml).as(sheet.template() + " -> " + key).contains(key);
                }
            }
        }
    }

    /**
     * الإيصالات بعرض الرول، لا بعرض A4.
     *
     * <p>عرضٌ منسيّ على 595 يخرج من طابعة حرارية مقصوصاً من طرفيه، ولا شيء في الترجمة ولا
     * في الملء يقوله - الورقة تبدو سليمة حتى تخرج من الجهاز عند العميل.</p>
     */
    @Test
    void receiptTemplatesAreRollWidthAndSinglePage() throws Exception {
        for (String template : List.of("PaymentReceipt.jrxml", "ShiftSummary.jrxml")) {
            String xml = Files.readString(REPORTS_DIR.resolve(template));
            assertThat(xml).as(template)
                    .contains("pageWidth=\"227\"")
                    .contains("isIgnorePagination=\"true\"");
        }
    }

    /** يملأ الكشف بقيم يساوي كلٌّ منها اسم معامله، ليُعرف في الورقة ما جاء من أين */
    private JasperPrint fillEnrollments(boolean showCenter) throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        for (String key : new String[]{"CENTER_NAME", "CENTER_PHONE", "REPORT_TITLE", "STUDENT_NAME",
                "STUDENT_DETAILS", "SUMMARY", "COL_GROUP", "COL_JOINED", "COL_LEFT", "COL_HELD",
                "COL_ATTENDED", "COL_RATE", "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"}) {
            parameters.put(key, key);
        }
        parameters.put("SHOW_CENTER", showCenter);
        parameters.put("LOGO_PATH", null);
        parameters.put("HEADER_REPORT", compiled("CenterHeader.jrxml"));

        List<EnrollmentReportRow> rows = List.of(
                new EnrollmentReportRow("مجموعة الأحد", "2026-01-05", "مستمر", "12", "10", "83%"),
                new EnrollmentReportRow("Sunday Group", "2026-02-01", "2026-03-01", "8", "4", "50%"));

        return JasperFillManager.fillReport(compiled("StudentEnrollments.jrxml"), parameters,
                new JRBeanCollectionDataSource(rows));
    }

    private JasperReport compiled(String fileName) throws Exception {
        try (InputStream stream = Files.newInputStream(REPORTS_DIR.resolve(fileName))) {
            return JasperCompileManager.compileReport(stream);
        }
    }

    private List<Path> templates() throws Exception {
        try (Stream<Path> files = Files.list(REPORTS_DIR)) {
            return files.filter(path -> path.toString().endsWith(".jrxml")).toList();
        }
    }
}
