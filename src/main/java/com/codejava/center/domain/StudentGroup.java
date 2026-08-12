package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "student_groups",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_student_group",
                columnNames = {"student_id", "group_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CourseGroup group;

    @Column(nullable = false)
    private LocalDate joinDate;

    /**
     * يوم الخروج من المجموعة، و{@code null} تعني اشتراكاً سارياً.
     *
     * <p>وجوده هو ما يجعل "كم حصة حضرها الطالب" سؤالاً له جواب: بدونه تُقارَن حصص
     * الطالب بكل حصص المجموعة منذ إنشائها، فيظهر من التحق الشهر الماضي وكأنه غائب عن
     * عشرين حصة عُقدت قبل التحاقه. ومعه تُحسب الحصص داخل مدة اشتراكه وحدها.</p>
     *
     * <p>يُمسح عند العودة إلى المجموعة نفسها: العضوية تُعاد تفعيلها ولا يُنشأ صف جديد،
     * ويصير تاريخ الاشتراك هو تاريخ العودة.</p>
     */
    private LocalDate leaveDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
