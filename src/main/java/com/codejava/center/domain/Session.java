package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "sessions")
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

    // مؤشر لمعرفة ما إذا كانت الحصة "مفتوحة" الآن لاستقبال الطلاب على البوابة
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = false;

    // مؤشر لمعرفة ما إذا تم محاسبة المعلم على هذه الحصة أم لا
    @Column(nullable = false)
    @Builder.Default
    private boolean isPaidOut = false;
}