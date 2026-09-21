package com.codejava.center.service;

/**
 * ماذا جرى لنسخةٍ بعد كتابتها: أخرجت من هذا الجهاز أم لا.
 *
 * <p><b>ثلاث حالات لا اثنتان</b>، وهي نفسُ قسمة {@code SendResult} في الرسائل: لم
 * يُطلب إخراجُها أصلاً، أو خرجت، أو طُلب ولم تخرج. وجمعُ الأولى والثالثة في "لا" واحدة
 * هو العطب بعينه - سنترٌ لم يطلب شيئاً وسنترٌ فشل رفعُه يقرأهما المشغّل سواءً، فيبحث
 * في الأول ويسكت عن الثاني.</p>
 *
 * @param requested هل ثمّة وجهةٌ مضبوطة؟ {@code false} يعني قراراً لا عطلاً
 * @param locator   ما يدلّ على النسخة عند الوجهة، لسطر سجلّ المراقبة
 * @param problem   سببُ عدم الخروج بجملته المترجَمة؛ رقمٌ وحده لا يقول لمن يقرأ ما يفعل
 */
public record OffsiteCopy(boolean requested, boolean stored, String locator, String problem) {

    /** لا وجهةَ مضبوطة: لا شيء يُقال، ولا شيء يُنبَّه عليه */
    public static OffsiteCopy notRequested() {
        return new OffsiteCopy(false, false, null, null);
    }

    public static OffsiteCopy stored(String locator) {
        return new OffsiteCopy(true, true, locator, null);
    }

    public static OffsiteCopy failed(String problem) {
        return new OffsiteCopy(true, false, null, problem);
    }

    /** طُلب الإخراجُ ولم يقع: هو وحده ما يستحقّ أن يُقال وأن يُنبَّه عليه */
    public boolean failedAfterBeingAsked() {
        return requested && !stored;
    }

    /** ما يُكتب في سطر سجلّ المراقبة بجوار حصيلة الحذف */
    public String details() {
        if (!requested) {
            return "offsite=off";
        }
        return stored ? "offsite=" + locator : "offsite=failed";
    }
}
