package com.codejava.center.domain;

import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.util.I18n;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * تنبيه واحد ظهر في صندوق التنبيهات.
 *
 * <h2>لا يُخزَّن نصّ مترجَم</h2>
 *
 * <p>الصف يحمل {@link AlertType} و{@link #args}، وتُبنى الجملة عند العرض بلغة الواجهة
 * الحالية. لو خُزّنت الجملة جاهزةً لَتجمّد كل تنبيه على اللغة التي كانت مضبوطة على
 * الجهاز الذي أطلقه، فظهر صندوق واحد بلغتين - وهي نفس القاعدة التي يقوم عليها
 * {@link AuditLog}.</p>
 *
 * <p>{@link #args} وسائط {@code MessageFormat} مفصولة بـ {@code |}، وكلها قيم محايدة
 * لغوياً: اسم، عدد، مبلغ بصيغة {@code MoneyUtils.format} بلا رمز عملة. رمز العملة جزء
 * من نصّ القالب في كل لغة، فلا تظهر واجهة إنجليزية بمبالغ مذيَّلة بـ "ج.م".</p>
 *
 * <h2>{@link #dedupeKey} هو ما يجعل النظام موثوقاً</h2>
 *
 * <p>مفتاح فريد على مستوى قاعدة البيانات يجمع النوع والكيان ونافذة التهدئة. السنتر فيه
 * أكثر من جهاز، وكلّها تفتح البرنامج فيعمل في كلٍّ منها مجدوِل مستقل: بلا هذا القيد
 * يصحو ثلاثة أجهزة على نفس الموعد فيظهر التنبيه ثلاث مرات. القيد في القاعدة لا في
 * الكود عن قصد - الفحص ثم الإدراج بينهما فجوة يمرّ منها جهازان بالضبط.</p>
 *
 * <p>و{@code entityId} قد يكون {@code null} (فشل نسخة احتياطية لا كيان له)، ولذلك يدخل
 * في المفتاح نصّاً بشرطة بدلاً منه: الفهرس الفريد في MySQL يعتبر كل {@code NULL}
 * مختلفاً عن الآخر، فكان تنبيه فشل النسخة سيتكرر بلا حدّ.</p>
 */
@Entity
@Table(name = "alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_dedupe", columnNames = "dedupe_key"),
        indexes = {
                // الصندوق يُفتح دائماً على "غير المقروء أولاً، ثم الأحدث"
                @Index(name = "idx_alert_raised_at", columnList = "raised_at"),
                @Index(name = "idx_alert_acknowledged", columnList = "acknowledged_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Alert {

    /** الفاصل بين وسائط الرسالة؛ لا يظهر في اسم طالب ولا في مبلغ */
    public static final String ARG_SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

    /**
     * الدرجة وقت الإطلاق، لا الدرجة المضبوطة الآن في القاعدة: خفض درجة قاعدة اليوم
     * لا يصحّ أن يعيد كتابة تاريخ ما كان حرجاً أمس.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false)
    private LocalDateTime raisedAt;

    /** معرّف الطالب أو المجموعة أو الحصة؛ {@code null} لتنبيهات لا كيان لها */
    private Long entityId;

    /** اسمه وقت الإطلاق، حتى يبقى السطر مقروءاً بعد حذف الصف */
    @Column(length = 150)
    private String entityLabel;

    /** وسائط نصّ الرسالة مفصولة بـ {@link #ARG_SEPARATOR}؛ محايدة لغوياً */
    @Column(length = 500)
    private String args;

    /** راجع شرح الصف: القيد الفريد الذي يمنع تكرار التنبيه بين أجهزة السنتر */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    /** لحظة قراءة المستخدم للتنبيه ووضع علامة "تمت المعالجة"؛ {@code null} = ما زال قائماً */
    private LocalDateTime acknowledgedAt;

    @Column(length = 50)
    private String acknowledgedBy;

    /*
     * لا عمود هنا لما أُرسل إلى ولي الأمر: notification_logs يحمله كاملاً - الرقم كما
     * أُرسل، والنصّ، والقناة، واللحظة - وعمودٌ ثانٍ لنفس الواقعة يفتح باب اختلافهما،
     * فيقول أحدهما إن الرسالة وصلت ويقول الآخر إنها لم تصل. وهو الفصل نفسه الذي جعل
     * تنبيه محاولات الدخول يُعدّ من audit_logs بدل عدّاد خاص به.
     */

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    /**
     * نصّ التنبيه بلغة الواجهة الحالية، مبنيّاً من القالب والوسائط.
     *
     * <p>الوسائط تُمرَّر نصوصاً كما خُزّنت: {@code MessageFormat} لا يعيد تنسيق النصّ،
     * فيظهر المبلغ والعدد كما كُتبا بالضبط بدل أن يفرض عليهما تنسيق اللغة الحالية
     * (فاصلة آلاف تظهر ثم تختفي بتبديل اللغة).</p>
     */
    public String describe() {
        String key = "alert.message." + type.name();
        return args == null || args.isBlank()
                ? I18n.get(key)
                : I18n.format(key, (Object[]) args.split("\\" + ARG_SEPARATOR, -1));
    }
}
