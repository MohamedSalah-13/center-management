package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "students", indexes = {
        @Index(name = "idx_student_barcode", columnList = "barcode", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 15)
    private String phone;

    @Column(length = 15)
    private String parentPhone;

    @Column(length = 50)
    private String schoolLevel;

    // هل حساب الطالب مفعل أم موقوف
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;
}