package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "course_groups")
@Getter // استبدال Data
@Setter // استبدال Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // حصر المقارنة في الحقول المحددة فقط
public class CourseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // تحديد الـ ID كمعيار وحيد لدالة equals لتجنب لمس الحقول الكسولة
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    private Integer maxCapacity;

    // BigDecimal وليس Double: سعر الحصة يدخل في كل حسابات الإيراد والعمولات
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sessionPrice;
}