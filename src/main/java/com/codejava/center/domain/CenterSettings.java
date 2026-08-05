package com.codejava.center.domain;

import com.codejava.center.domain.enums.BackupFrequency;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    /*
     * موعد النسخ التلقائي. كل هذه الأعمدة تقبل null لأن قاعدة بيانات مُرقّاة من نسخة أقدم
     * لن تحملها، و{@link com.codejava.center.service.BackupSchedule} يملأ الناقص بالموعد
     * القديم نفسه (يومياً 2 صباحاً) فلا يتغيّر سلوك من لم يفتح شاشة الإعدادات بعد الترقية.
     *
     * الجدولة تخصّ السنتر لا الجهاز — على عكس الطابعة واللغة — لأنها سياسة حماية بيانات
     * واحدة: الوقت الذي يكون فيه السنتر مغلقاً وقاعدة البيانات ساكنة.
     */
    @Enumerated(EnumType.STRING)
    private BackupFrequency backupFrequency;

    private LocalTime backupTime;

    /** يوم الأسبوع للتكرار الأسبوعي: 1 = الاثنين … 7 = الأحد */
    private Integer backupDayOfWeek;

    private Integer backupDayOfMonth;

    /**
     * عدد النسخ المحتفظ بها في المجلد؛ الأقدم يُحذف بعد كل نسخة ناجحة.
     * {@code null} أو صفر يعني الاحتفاظ بالكل — ملف يومي في مجلد على قرص صغير يملؤه
     * في أشهر، ولا تُكتشف امتلاء المساحة إلا حين تفشل النسخة التي كنت تحتاجها.
     */
    private Integer backupRetentionCount;

    /**
     * آخر نسخة تلقائية <b>ناجحة</b>. تُستعمل لتعويض موعد فات والجهاز مطفأ، ولتُظهر شاشة
     * الإعدادات تاريخاً قديماً حين تفشل النسخ ليلة بعد ليلة بدل أن يمرّ الفشل بصمت.
     */
    private LocalDateTime lastAutoBackupAt;

    /**
     * تاريخ بداية دفتر الحسابات: لا تُحتسب في رصيد الطالب أي حركة قبل هذا التاريخ.
     * الغرض منه أن الدفعات القديمة المسجَّلة قبل تطبيق نظام الرصيد ليست لها خصومات
     * مقابلة، فلو احتُسبت لظهر كل طالب دائناً بمبلغ كبير وهمي.
     * القيمة null تعني احتساب كل الحركات (المناسب للتركيب الجديد بلا بيانات سابقة).
     */
    private LocalDate ledgerStartDate;
}