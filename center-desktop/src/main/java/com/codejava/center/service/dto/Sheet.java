package com.codejava.center.service.dto;

import com.codejava.center.core.print.DocumentKind;
import com.codejava.center.util.I18n;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;

/**
 * ورقة جاسبر بعد ملئها وقبل أن يمسّها أي جهاز.
 *
 * <p>هذا هو ما تعيده {@code ReportService} الآن: الورقة نفسها، لا ما حدث لها. الخدمة كانت
 * تملأ ثم تطبع أو تكتب ملفاً مؤقتاً في الدالة نفسها، فكانت تحمل {@code javax.print} ومجلد
 * الملفات المؤقتة معها إلى كل من يستدعيها — وخادمٌ يطلب نفس الورقة ليردّها في جواب HTTP
 * لا طابعة له ولا مجلد مؤقت يُفتح للمستخدم.</p>
 *
 * <p>ثلاثة حقول لا أكثر، وكلها من جهة التعبئة:</p>
 *
 * <ul>
 *   <li>{@code print} الورقة المملوءة. تبقى {@link JasperPrint} لا {@code byte[]} لأن
 *       الطباعة المباشرة تأخذها كما هي، وتحويلها PDF ثم إعادة قراءته عملٌ ضائع.</li>
 *   <li>{@code kind} تقرير على ورق مقطوع أم إيصال على رول — وهو ما يختار به المسلِّم
 *       الطابعة، فالجهاز الواحد قد يكون موصولاً باثنتين.</li>
 *   <li>{@code fileNamePrefix} بادئة اسم الملف حين يُكتب. من يملأ الورقة يعرف ما هي،
 *       ومن يسلّمها لا يعرف.</li>
 * </ul>
 *
 * @param print          الورقة المملوءة
 * @param kind           نوع المستند، وهو ما يحدّد الطابعة والورق
 * @param fileNamePrefix بادئة اسم الملف، مثل {@code "arrears_"}
 */
public record Sheet(JasperPrint print, DocumentKind kind, String fileNamePrefix) {

    /**
     * الورقة PDF في الذاكرة.
     *
     * <p>{@code byte[]} لا مسار ملف: من يكتبها على القرص هو من يعرف أين يكتبها — ملفٌ
     * مؤقت يُفتح للمستخدم على Desktop، أو جسمُ جوابٍ على خادم لا يكتب شيئاً أصلاً.</p>
     */
    public byte[] toPdf() {
        try {
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw generationFailed(e);
        }
    }

    /**
     * فشل في جاسبر برسالة مترجَمة تحمل نصّ الخطأ الأصلي.
     *
     * <p>هنا لا في {@code ReportService}: الملء يفشل هناك، والتحويل إلى PDF والطباعة
     * يفشلان عند المسلِّم، والثلاثة فشلٌ واحد في عين من ينظر إلى الشاشة. ورسالة
     * {@code JRException} وحدها تقول "null" في أحوال كثيرة، فاسم الصنف بديلها.</p>
     */
    public static IllegalStateException generationFailed(JRException e) {
        return new IllegalStateException(I18n.format("error.report.generateFailed",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
    }
}
