package com.codejava.center.domain;

import com.codejava.center.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * سجل ما أُرسل لأولياء الأمور.
 *
 * <p>غرضه الأساسي منع التكرار: بدونه يُرسَل إشعار الغياب نفسه في كل مرة يُفتح فيها
 * التقرير، فيصل ولي الأمر عدة رسائل عن غياب واحد. وغرضه الثاني التوثيق: أن يستطيع
 * السنتر إثبات أنه أبلغ.</p>
 */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_student_type", columnList = "student_id, type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    /** الرقم بالصيغة الدولية كما أُرسل فعلاً، لا كما هو مخزَّن في بيانات الطالب */
    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    /** القناة المستخدمة، حتى يبقى السجل مفهوماً بعد تغيير المزوّد */
    @Column(nullable = false, length = 30)
    private String channel;
}
