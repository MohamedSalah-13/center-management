package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_group_date",
                columnNames = {"group_id", "session_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // حصر المقارنة في الـ ID لتجنب لمس الحقول الكسولة
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // علاقة الحصة بالمجموعة (كل حصة تابعة لمجموعة معينة)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CourseGroup group;

    @Column(nullable = false)
    private LocalDate sessionDate;

    /**
     * لحظة فتح الحصة فعلاً، ولحظة إغلاقها.
     *
     * <p>{@code LocalDateTime} لا {@code LocalTime}: الحصة تُترك مفتوحة إلى صباح اليوم
     * التالي أحياناً - وهي الحالة التي يقوم لها تنبيه {@code SESSION_LEFT_OPEN} - فساعةٌ
     * بلا يومها تجعل حصة انتهت في التاسعة صباحاً تبدو أقصر من الصفر.</p>
     *
     * <p>وهما غير {@code sessionDate}: التاريخ هو اليوم الذي تُنسب إليه الحصة ويُختار في
     * الشاشة (قد يُفتح كشف يوم مضى)، وهذان ما جرى بالفعل على الساعة. وكلاهما يقبل
     * {@code NULL} لأن الحصص المسجَّلة قبل هذه الإضافة لا تحمل وقتاً، وصفر أو تاريخ
     * مُختلَق يقول عنها ما لا يُعلم.</p>
     */
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    // مؤشر لمعرفة ما إذا كانت الحصة "مفتوحة" الآن لاستقبال الطلاب على البوابة
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = false;

    // مؤشر لمعرفة ما إذا تم محاسبة المعلم على هذه الحصة أم لا
    @Column(nullable = false)
    @Builder.Default
    private boolean isPaidOut = false;
}
