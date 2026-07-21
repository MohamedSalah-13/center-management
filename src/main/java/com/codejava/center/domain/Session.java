package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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