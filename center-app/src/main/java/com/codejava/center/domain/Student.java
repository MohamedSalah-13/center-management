package com.codejava.center.domain;

import com.codejava.center.domain.enums.SchoolLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "students", indexes = {
        @Index(name = "idx_student_barcode", columnList = "barcode", unique = true),
        // شاشة التسجيل تقرأ المسجَّلين حالياً مرتَّبين بالاسم؛ العمودان معاً حتى يخدم
        // الفهرسُ الشرطَ والترتيبَ في مرور واحد. مفرداً كان عديم الفائدة: قيمتان لا غير
        @Index(name = "idx_student_active", columnList = "is_active, name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 15)
    private String phone;

    @Column(length = 15)
    private String parentPhone;

    /** الصف الدراسي: قيمة ثابتة تُخزَّن باسمها لا نصاً مترجَماً - انظر {@link SchoolLevel} */
    @Enumerated(EnumType.STRING)
    private SchoolLevel schoolLevel;

    /**
     * مسجَّل حالياً. الإيقاف هنا هو "الأرشفة" في الشاشة، وليس علماً للعرض وحده:
     * {@code AttendanceService} يردّ الطالب الموقوف على البوابة، وهو المخرج الوحيد
     * لطالب تخرّج أو انقطع - إذ يمنع حذفَه ما له من حضور وحركات مالية.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;
}