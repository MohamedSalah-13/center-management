package com.codejava.center.reports;

import com.codejava.center.service.dto.EnrollmentReportRow;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
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
        Map<String, Object> parameters = new HashMap<>();
        for (String key : new String[]{"CENTER_NAME", "CENTER_PHONE", "REPORT_TITLE", "STUDENT_NAME",
                "STUDENT_DETAILS", "SUMMARY", "COL_GROUP", "COL_JOINED", "COL_LEFT", "COL_HELD",
                "COL_ATTENDED", "COL_RATE", "PRINTED_AT", "PAGE_LABEL", "NO_ROWS"}) {
            parameters.put(key, key);
        }

        List<EnrollmentReportRow> rows = List.of(
                new EnrollmentReportRow("مجموعة الأحد", "2026-01-05", "مستمر", "12", "10", "83%"),
                new EnrollmentReportRow("Sunday Group", "2026-02-01", "2026-03-01", "8", "4", "50%"));

        JasperReport report;
        try (InputStream stream = Files.newInputStream(REPORTS_DIR.resolve("StudentEnrollments.jrxml"))) {
            report = JasperCompileManager.compileReport(stream);
        }
        JasperPrint print = JasperFillManager.fillReport(report, parameters,
                new JRBeanCollectionDataSource(rows));

        assertThat(print.getPages()).as("صفحات الكشف").isNotEmpty();

        // القيم تصل الورقة فعلاً: عمود فارغ لا يرفع استثناءً، فيُفحص محتوى النصّ نفسه
        assertThat(JasperExportManager.exportReportToXml(print))
                .contains("مجموعة الأحد")
                .contains("83%")
                .contains("COL_GROUP");
    }

    private List<Path> templates() throws Exception {
        try (Stream<Path> files = Files.list(REPORTS_DIR)) {
            return files.filter(path -> path.toString().endsWith(".jrxml")).toList();
        }
    }
}
