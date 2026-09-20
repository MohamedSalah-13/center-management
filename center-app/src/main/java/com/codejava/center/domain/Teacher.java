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

    @Column(nullable = false, length = 50)
    private String subject;

    @Column(nullable = false, length = 20)
    private String commissionType;

    // BigDecimal وليس Double: قيمة العمولة تُضرب وتُقسم في حساب مستحقات المعلم
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionValue;
}