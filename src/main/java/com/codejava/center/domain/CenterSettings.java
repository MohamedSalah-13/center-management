package com.codejava.center.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "center_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CenterSettings {

    @Id
    @EqualsAndHashCode.Include
    @Builder.Default // بدونها يتجاهل الـ Builder القيمة الابتدائية ويضع null في الـ ID
    private Long id = 1L; // تثبيت الـ ID برقم 1 لأننا نحتاج صف واحد فقط للإعدادات

    private String centerName;
    private String centerPhone;
    private String logoPath;
    private String backupPath;
    private boolean autoBackupEnabled;

    /**
     * تاريخ بداية دفتر الحسابات: لا تُحتسب في رصيد الطالب أي حركة قبل هذا التاريخ.
     * الغرض منه أن الدفعات القديمة المسجَّلة قبل تطبيق نظام الرصيد ليست لها خصومات
     * مقابلة، فلو احتُسبت لظهر كل طالب دائناً بمبلغ كبير وهمي.
     * القيمة null تعني احتساب كل الحركات (المناسب للتركيب الجديد بلا بيانات سابقة).
     */
    private LocalDate ledgerStartDate;
}