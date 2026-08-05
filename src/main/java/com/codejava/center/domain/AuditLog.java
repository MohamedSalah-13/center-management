package com.codejava.center.domain;

import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * حدث واحد في سجل المراقبة: من فعل ماذا ومتى.
 *
 * <p>الغرض إثبات ما جرى، لا تسريع أي شيء. لذلك كل حقل هنا مكتوب ليبقى مفهوماً بعد
 * اختفاء ما يشير إليه:</p>
 *
 * <ul>
 *   <li><b>{@code actorUsername} نصّ لا مفتاح أجنبي إلى {@code users}.</b> لو كان مفتاحاً
 *       أجنبياً لَمَنَع حذف المستخدم أو حذف أثره معه، وهو بالضبط ما يفعله من يريد إخفاء
 *       تصرّفاته: يحذف الحساب. الاسم هنا نسخة وقت الحدث.</li>
 *   <li><b>{@code entityLabel} اسم الهدف وقت وقوع الحدث</b> (اسم الطالب، اسم المجموعة).
 *       {@code entityId} وحده يصير رقماً بلا معنى بعد حذف الصف، وسطر "حذف الطالب رقم 412"
 *       لا يخبر أحداً بشيء.</li>
 *   <li><b>{@code action} قيمة enum لا نصّ مقروء.</b> النص المقروء يُبنى عند العرض بلغة
 *       الواجهة الحالية؛ تخزينه مترجَماً كان سيجمّد كل سطر على لغة كاتبه.</li>
 * </ul>
 *
 * <p>{@code actorUsername} يقبل NULL ويعني "النظام": النسخة الاحتياطية المجدولة تعمل من
 * خيط المجدوِل بلا جلسة مستخدم، ولا يصحّ نسبتها إلى آخر من سجّل دخوله.</p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        // الشاشة تصفّي دائماً بفترة زمنية أولاً ثم بالمستخدم؛ السجل ينمو بلا حدّ
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_actor", columnList = "actor_username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** اسم المستخدم وقت الحدث؛ {@code null} يعني أن النظام نفسه هو الفاعل (مهمة مجدولة) */
    @Column(length = 50)
    private String actorUsername;

    /** صلاحيته وقت الحدث، لا صلاحيته الآن: قد تكون تغيّرت بعده */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    /** معرّف الصف المتأثر إن وُجد؛ يبقى مفيداً للربط ما دام الصف قائماً */
    private Long entityId;

    /** اسمه وقت الحدث، حتى يبقى السطر مقروءاً بعد حذف الصف */
    @Column(length = 150)
    private String entityLabel;

    /** المبلغ للأحداث المالية وحدها؛ NULL في غيرها فلا يظهر صفر مضلِّل */
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * تفاصيل إضافية بصيغة محايدة لغوياً ({@code price=50.00; capacity=20})،
     * أو نصّ العملية نفسه كما حُفظ في سجلّها (وصف الحركة المالية مثلاً).
     */
    @Column(length = 500)
    private String details;

    /** نجح الحدث أم رُفض/فشل؛ محاولة الوصول المرفوضة والنسخة الفاشلة أهمّ ما في السجل */
    @Column(nullable = false)
    private boolean successful;
}
