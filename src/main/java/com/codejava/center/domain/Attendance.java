package com.codejava.center.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false)
    private LocalDateTime timeIn;

    /**
     * لحظة انصراف الطالب، أو {@code NULL} إن لم يُسجَّل انصرافه.
     *
     * <p>و{@code NULL} تبقى كما هي بعد إغلاق الحصة: ختمُ وقت الإغلاق على من لم يمرّر
     * كارنيهه عند خروجه يجعل من غادر في السابعة يبدو كأنه بقي إلى العاشرة، ويُقرأ الرقم
     * بعد شهر على أنه واقع. "لم يُسجَّل" معلومةٌ صحيحة، والوقت المخترَع معلومةٌ كاذبة.</p>
     *
     * <p>{@code LocalDateTime} لا {@code LocalTime} لسبب {@link Session#getEndedAt()}
     * نفسه: حصةٌ تمتدّ إلى ما بعد منتصف الليل تجعل ساعةً بلا يومها تقول إن الطالب انصرف
     * قبل أن يدخل.</p>
     */
    private LocalDateTime timeOut;
}