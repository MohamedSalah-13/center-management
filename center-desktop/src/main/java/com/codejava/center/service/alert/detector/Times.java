package com.codejava.center.service.alert.detector;

import java.time.LocalTime;

/**
 * صياغة الساعة داخل وسائط التنبيه.
 *
 * <p>{@code String.format} صراحةً لا {@code DateTimeFormatter} ولا {@code toString}:
 * الوسائط تُخزَّن في قاعدة البيانات وتُقرأ بعد شهر بلغةٍ قد تكون تبدّلت، فلا يصحّ أن
 * يتبع شكلُها لغةَ الجهاز الذي كتبها. و{@code LocalTime.toString} يُسقط الدقائق
 * الصفرية فتُخزَّن الرابعة عصراً "16" لا "16:00" - رقمٌ لا يُقرأ ساعةً.</p>
 */
final class Times {

    private Times() {
    }

    /** الساعة بصيغة {@code HH:mm}، محايدة لغوياً وثابتة الشكل */
    static String clock(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
