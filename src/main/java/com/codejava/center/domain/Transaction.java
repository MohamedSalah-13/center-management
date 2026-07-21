package com.codejava.center.domain;

import com.codejava.center.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    // القيمة المالية (يفضل دائماً استخدام الأرقام الموجبة، ونفرق بالنوع INCOME/EXPENSE)
    @Column(nullable = false)
    private Double amount;

    // وصف الحركة (مثال: "اشتراك شهر 7 رياضيات" أو "فاتورة كهرباء")
    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    // --- العلاقات الاختيارية ---

    // من دفع هذا المبلغ؟ (تكون null في حالة المصروفات)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    // ما هي المجموعة الخاصة بهذا المبلغ؟ (تسهل حساب أرباح المجموعة لاحقاً)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private CourseGroup group;

    // من استلم أو صرف هذا المبلغ؟ (تكون null إذا لم نستلم للموظفين بعد)
    // @Column(length = 50)
    // private String createdBy;
}