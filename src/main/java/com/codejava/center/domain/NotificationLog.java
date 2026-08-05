package com.codejava.center.domain;

import com.codejava.center.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * سجل ما أُرسل لأولياء الأمور.
 *
 * <p>غرضه الأساسي منع التكرار: بدونه يُرسَل إشعار الغياب نفسه في كل مرة يُفتح فيها
 * التقرير، فيصل ولي الأمر عدة رسائل عن غياب واحد. وغرضه الثاني التوثيق: أن يستطيع
 * السنتر إثبات أنه أبلغ.</p>
 *
 * <p>هو أيضاً حاجز التكرار في المسار التلقائي، ومستقلٌّ عن
 * {@link Alert#getDedupeKey()} عن قصد: صفّ الصندوق الداخلي يُكتب فور اكتشاف الحالة،
 * أما هذا فلا يُكتب إلا بعد نجاح الإرسال فعلاً. لو كان الحاجز واحداً لَامتنع النظام
 * عن إعادة المحاولة بعد رسالة فشلت، ظانّاً أن ولي الأمر أُبلغ.</p>
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

    /**
     * نوع الإشعار. صار {@link AlertType} بعد توحيد المفردات، وقيمتا {@code ABSENCE}
     * و{@code ARREARS} باقيتان باسميهما فالصفوف القديمة تُقرأ كما هي؛ لم يتغيّر إلا
     * اتساع العمود ليسع أسماء الأنواع الجديدة.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

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
