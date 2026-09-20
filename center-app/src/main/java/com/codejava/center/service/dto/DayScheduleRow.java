package com.codejava.center.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * سطر واحد في جدول حصص اليوم: مجموعة يجتمع موعدها اليوم، وما جرى لها فعلاً.
 *
 * <p>صنف بقارئات JavaBean وقيم نصّية مُهيّأة، لنفس سببَي {@link GroupListRow}: جاسبر يطلب
 * {@code getGroupName()} ولا يعرف قارئات السجلّات، والمواعيد والحالات تُصاغ بـ
 * {@code WeekDays} و{@code Moments} و{@code I18n} - لا بتعبيرات داخل ملف تصميم لا يعرف
 * لغة الواجهة.</p>
 *
 * <p>الصفّ نفسه يخدم الشاشة والورقة: ما يُطبع هو ما كان أمام الموظف، لا ترتيب آخر يحتاج
 * أن يُقرأ من جديد.</p>
 */
@Getter
@RequiredArgsConstructor
public class DayScheduleRow {

    private final String groupName;
    private final String teacherName;
    private final String level;

    /** الموعد المجدول أسبوعياً. فارغ يعني حصةً فُتحت خارج جدول المجموعة */
    private final String scheduledTime;

    private final String status;

    /** ما جرى فعلاً على الساعة: لحظة الفتح ولحظة الإغلاق */
    private final String startedAt;
    private final String endedAt;

    /** الحاضرون من المشتركين في هذا اليوم: "12 / 20" */
    private final String attendance;

    /**
     * الحالة نفسها مرة أخرى، رمزاً لا نصاً.
     *
     * <p>لا تُعلنها الورقة - جاسبر لا يقرأ إلا الحقول المعلَنة في التصميم - وإنما تحتاجها
     * الشاشة للتظليل ولعدّ "جارية الآن" و"لم تُفتح". وعدُّها بمقارنة {@code status}
     * بنصٍّ مترجَم يجعل الملخّص يقرأ صفراً في اللغة التي لم تُقارَن بها.</p>
     */
    private final State state;

    public boolean isOpen() {
        return state == State.OPEN;
    }

    /** ما جرى للمجموعة اليوم: لم تُفتح لها حصة، أو حصتها جارية الآن، أو انتهت */
    public enum State {
        NOT_OPENED, OPEN, CLOSED
    }
}
