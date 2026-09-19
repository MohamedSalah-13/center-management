package com.codejava.center.domain;

import com.codejava.center.domain.converter.MeetingDaysConverter;
import com.codejava.center.domain.enums.SchoolLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /**
     * الصف الذي تخدمه المجموعة، وهو شرط قبول الطالب فيها.
     *
     * <p>العمود يقبل {@code null} في قاعدة البيانات وحدها: المجموعات التي أُنشئت قبل هذه
     * الميزة ليس لها صف، ولا يصح أن يمنع الترحيل تشغيل البرنامج عند العميل. أما
     * {@code CourseGroupService.saveGroup} فيرفض الحفظ بلا صف، فأول تعديل على مجموعة
     * قديمة يستكمل بياناتها، والاشتراك في مجموعة بلا صف مرفوض حتى يُضبط.</p>
     */
    @Enumerated(EnumType.STRING)
    private SchoolLevel schoolLevel;

    /** أيام انعقاد المجموعة في الأسبوع - محوَّلة إلى عمود نصي واحد، انظر {@link MeetingDaysConverter} */
    @Convert(converter = MeetingDaysConverter.class)
    @Column(name = "meeting_days", length = 100)
    @Builder.Default
    private Set<DayOfWeek> meetingDays = new LinkedHashSet<>();

    /** وقت البداية، مشترك بين كل أيام المجموعة */
    private LocalTime startTime;

    private LocalTime endTime;

    /**
     * هل يُشتق الاسم من (الصف + المعلم + الأيام + الساعة) عند كل حفظ؟
     *
     * <p>الافتراضي نعم، فيبقى الاسم صادقاً بعد تغيير الموعد أو المعلم. ومن أراد اسماً
     * خاصاً ("مجموعة المتفوقين") يوقف الاشتقاق صراحةً، وعندها يصبح الاسم بياناً يكتبه
     * المستخدم ولا يلمسه النظام.</p>
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean autoName = true;
}