package com.codejava.center.domain;

import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ضبط نوع تنبيه واحد: مفعَّل أم لا، لمن يذهب، وبأي حدود.
 *
 * <p>صف واحد لكل {@link AlertType}، يُنشأ بقيم النوع الافتراضية أول مرة يُسأل عنه. لا
 * تُزرع الصفوف في الترحيل عن قصد: إضافة نوع جديد في نسخة لاحقة كانت ستحتاج ترحيلاً
 * ثانياً يزرع صفّه، ونسيانه يعني نوعاً موجوداً في الشاشة بلا ضبط. الإنشاء عند الطلب
 * يجعل الكود وحده مصدر القائمة.</p>
 *
 * <p>الحقول الرقمية تقبل {@code null} وتعني "استعمل افتراضي النوع": نوع أُضيف له
 * معامل لم يكن موجوداً يجد قيمةً معقولة بدل صفر يُعطّل القاعدة بصمت.</p>
 *
 * <p>{@code updatedBy} نصّ لا مفتاح أجنبي إلى {@code users}، للسبب نفسه في
 * {@link AuditLog}: من يوقف تنبيهاً ثم يُحذف حسابه يجب أن يبقى اسمه مقروءاً هنا.</p>
 */
@Entity
@Table(name = "alert_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_rule_type", columnNames = "type"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    /** معناه يختلف باختلاف النوع؛ الشاشة تشرحه بمفتاح خاص بالنوع */
    private Integer threshold;

    /** طول الفترة التي يفحصها التنبيه بالأيام */
    private Integer windowDays;

    /** أقل مدة بين تنبيهين متطابقين؛ صفر يعني أن النوع حَدَثيّ لا مجدول */
    private Integer cooldownDays;

    private LocalDateTime updatedAt;

    @Column(length = 50)
    private String updatedBy;

    /**
     * قاعدة بقيم النوع الافتراضية. الوجهة داخلية دائماً - راجع
     * {@link AlertType#getDefaultAudience()}.
     */
    public static AlertRule defaultsFor(AlertType type) {
        return AlertRule.builder()
                .type(type)
                .enabled(true)
                .audience(type.getDefaultAudience())
                .severity(type.getDefaultSeverity())
                .threshold(type.getDefaultThreshold())
                .windowDays(type.getDefaultWindowDays())
                .cooldownDays(type.getDefaultCooldownDays())
                .build();
    }

    /** الحدّ المضبوط، أو افتراضي النوع إن كان الصف قديماً لا يحمله */
    public int thresholdOrDefault() {
        return orDefault(threshold, type.getDefaultThreshold());
    }

    public int windowDaysOrDefault() {
        return orDefault(windowDays, type.getDefaultWindowDays());
    }

    public int cooldownDaysOrDefault() {
        return orDefault(cooldownDays, type.getDefaultCooldownDays());
    }

    /** هل يُكتب سطر في صندوق التنبيهات لهذه القاعدة؟ */
    public boolean notifiesInternally() {
        return enabled && audience.includesInternal();
    }

    /**
     * هل تُرسل رسالة إلى ولي الأمر؟ يشترط أن يكون النوع صالحاً للإرسال أصلاً:
     * وجهة محفوظة قديماً على نوع لم يعد صالحاً لا يجوز أن تُخرج رسالة.
     */
    public boolean notifiesParents() {
        return enabled && audience.includesParents() && type.isParentCapable();
    }

    private static int orDefault(Integer value, Integer fallback) {
        if (value != null) {
            return value;
        }
        return fallback == null ? 0 : fallback;
    }
}
