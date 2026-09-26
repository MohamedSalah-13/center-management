package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false, foreignKey = @ForeignKey(name = "fk_teacher_subject"))
    private Subject subjectDefinition;

    public String getSubject() { return subjectDefinition == null ? null : subjectDefinition.getName(); }

    @Column(nullable = false, length = 20)
    private String commissionType;

    // BigDecimal وليس Double: قيمة العمولة تُضرب وتُقسم في حساب مستحقات المعلم
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionValue;
}