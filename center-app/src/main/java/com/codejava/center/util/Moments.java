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

    /**
     * الصيغة تُبنى بلغة القارئ لا بلغة الـ JVM.
     *
     * <p>{@code ofPattern} بلا لغة تقرأ {@link java.util.Locale#getDefault} - وهو ما
     * كان يصحّ ما دام برنامجُ سطح المكتب هو من يضبطه. طبقةُ الأعمال لم تعد تضبطه،
     * وخادمٌ يعمل بـ {@code ar-EG} يكتب "٠٩:١٥" بأرقام هندية في كشفٍ كلُّ أرقامه
     * لاتينية.</p>
     */
    private static final String TIME_ONLY = "HH:mm";
    private static final String DATE_AND_TIME = "yyyy-MM-dd HH:mm";

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
                ? instant.format(formatter(TIME_ONLY))
                : instant.format(formatter(DATE_AND_TIME));
    }

    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, I18n.current());
    }
}
