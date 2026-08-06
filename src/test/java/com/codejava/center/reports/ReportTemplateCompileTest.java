package com.codejava.center.reports;

import com.codejava.center.service.dto.EnrollmentReportRow;
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
