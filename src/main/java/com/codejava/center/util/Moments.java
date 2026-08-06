package com.codejava.center.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * لحظةٌ وقعت، مقروءةً بجوار اليوم الذي تُنسب إليه.
 *
 * <p>الساعة وحدها ما دامت اللحظة في ذلك اليوم، ومعها التاريخ إن خرجت عنه. الحصة تُترك
 * مفتوحة إلى صباح اليوم التالي أحياناً - وهي الحالة التي يقوم لها تنبيه
 * {@code SESSION_LEFT_OPEN} - و"09:15" وحدها في صفٍّ تاريخه أمس تُقرأ على أنها انتهت قبل
 * أن تبدأ. والتاريخ لا يُكتب في كل صف لأنه حينها تكرارٌ لعمود التاريخ المجاور في كل حصة
 * عادية.</p>
 *
 * <p>مكتوبة هنا لا في متحكّم: شاشة إدارة الحصص وجدول اليوم كلتاهما تعرض اللحظة نفسها،
 * ونسختان من القاعدة تعنيان صفحتين تختلفان في قراءة الحصة المفتوحة عبر منتصف الليل.</p>
 */
public final class Moments {

    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Moments() {
    }

    /**
     * @param instant اللحظة، و{@code null} تعني غير معلومة: حصة لم تُغلق بعد، أو حصة
     *                سُجّلت قبل أن يصير الوقت محفوظاً. خانة فارغة أصدق من رقم مُختلَق.
     * @param day     اليوم الذي تُنسب إليه اللحظة
     */
    public static String describe(LocalDateTime instant, LocalDate day) {
        if (instant == null) {
            return I18n.get("common.empty");
        }
        return instant.toLocalDate().equals(day)
                ? instant.format(TIME_ONLY)
                : instant.format(DATE_AND_TIME);
    }
}
