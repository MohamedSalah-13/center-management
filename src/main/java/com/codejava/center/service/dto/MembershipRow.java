package com.codejava.center.service.dto;

import java.time.LocalDate;

/**
 * عضوية طالب في مجموعة، ومعها حصاد مدتها.
 *
 * <p>{@code sessionsHeld} ليس عدد حصص المجموعة كلها بل الحصص المنعقدة <b>داخل مدة
 * اشتراك هذا الطالب</b>: من يوم التحاقه إلى يوم خروجه، أو إلى اليوم إن كان اشتراكه
 * سارياً. الفرق ليس تفصيلاً - بدونه يظهر من التحق أمس وكأنه غاب عن كل حصص الترم.</p>
 *
 * <p>وهو أيضاً ما يجعل عدد الحضور صالحاً للمقارنة بين طالبين التحقا في شهرين مختلفين،
 * وهي المقارنة التي يريدها من ينظر إلى أعداد الحضور بجانب مستحقات المعلم.</p>
 */
public record MembershipRow(
        Long studentId,
        String studentName,
        String barcode,
        String phone,
        String parentPhone,
        Long groupId,
        String groupName,
        LocalDate joinDate,
        LocalDate leaveDate,
        boolean active,
        long sessionsHeld,
        long sessionsAttended
) {

    /** نسبة الحضور من حصص مدة الاشتراك، أو {@code null} حين لا تنعقد حصة بعد */
    public Integer attendanceRate() {
        return sessionsHeld == 0 ? null : (int) Math.round((sessionsAttended * 100.0) / sessionsHeld);
    }
}
