package com.codejava.center.core.tenant;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * إلى متى دُفع اشتراكُ هذا السنتر، ومتى ينقضي فعلاً.
 *
 * <p>صنفٌ خالٍ من Spring ومن JPA عن قصد، كـ{@code BackupSchedule} و{@code BackupRetention}:
 * هذا هو الحساب الذي إن أخطأ في اتجاهٍ أغلق أبوابَ سنترٍ دافع، وإن أخطأ في الآخر خدم
 * سنتراً لم يدفع منذ أشهر - وكلاهما لا يُكتشف من قراءة الكود.</p>
 *
 * <h2>محورٌ ثانٍ، لا حالةٌ رابعة في {@code TenantStatus}</h2>
 *
 * <p>{@link TenantStatus} قرارُ المشغّل، وهذا حالُ المال، وهما يتغيّران لأسبابٍ مختلفة
 * وبيدين مختلفتين. وجمعُهما في عمودٍ واحد - أي إيقافٌ تلقائي يكتب {@code SUSPENDED} -
 * يُفقد الجوابَ عن سؤالٍ يُطرح بعد شهر: <b>أيُّهما أوقف هذا السنتر؟</b> ثم يُعيد الدفعُ
 * تشغيلَ سنترٍ أوقفه المشغّل لسببٍ آخر تماماً.</p>
 *
 * <p>وثمرةُ ذلك أن <b>لا مجدوِلَ يُوقف</b>: الانقضاء يُحسب عند القراءة كما يُحسب
 * {@code isOverdue}، فلا وجود لحالة "الدورة لم تعمل الليلة، فبقي سنترٌ منقضٍ يُخدَم".
 * والعطلُ الذي لا يمكن أن يقع لا يحتاج اختباراً.</p>
 *
 * <h2>وغيابُ التاريخ يعني "لا اشتراك يُتابَع"</h2>
 *
 * <p>لا "منقضٍ منذ الأزل". ثلاثةُ تركيباتٍ حقيقية تقع فيه: قاعدةُ منصّةٍ رُقّيت والعمودُ
 * فيها فارغ لكل سنتر، وخادمٌ يخدم سنتراً واحداً لا فوترةَ فيه أصلاً، وسنترٌ على اتفاقٍ
 * خاص. وقراءةُ الفراغ "لم يدفع" تُغلق الثلاثة دفعةً واحدة لحظةَ الترقية.</p>
 */
public record Subscription(LocalDate paidThrough) {

    /**
     * أيامُ السماح بعد انقضاء المدة قبل أن يتوقف شيء.
     *
     * <p>ليست تساهلاً: حوالةٌ مصرفية تتأخر يوماً، وإغلاقُ سنترٍ في منتصف ليلة اليوم
     * الأول هو كيف يُفقَد عميلٌ بسبب بنك. وهي نفسُها نافذةُ التنبيه قبل الانقضاء -
     * رقمٌ واحد لا رقمان يفترقان: المدةُ التي يُحتمل فيها تأخُّرُ الدفع هي المدةُ التي
     * ينبغي أن يُطالَب فيها به.</p>
     */
    public static final int GRACE_DAYS = 7;

    /** اشتراكٌ من تاريخٍ قد يكون غائباً - وهو الحال في كل صفٍّ قبل هذه الميزة */
    public static Subscription of(LocalDate paidThrough) {
        return new Subscription(paidThrough);
    }

    /** أثمّة اشتراكٌ يُتابَع أصلاً؟ */
    public boolean tracked() {
        return paidThrough != null;
    }

    /**
     * أانقضى - بعد أيام السماح؟
     *
     * <p>واليومُ الأخير مدفوع: {@code paidThrough} هو آخر يومٍ يشمله الدفع لا أولُ يومٍ
     * بعده، فالمقارنةُ على ما بعده. وخلطُ الاثنين يسرق من كل سنترٍ يوماً في كل شهر.</p>
     */
    public boolean lapsed(LocalDate today) {
        return tracked() && today.isAfter(paidThrough.plusDays(GRACE_DAYS));
    }

    /**
     * كم يوماً بقي حتى ينقضي الدفع - سالبةً بعد انقضائه.
     *
     * <p>من {@code paidThrough} لا من نهاية السماح: السماحُ احتياطٌ لا مدةٌ مباعة،
     * وعدُّه ضمن الباقي يقول للعميل إنه دفع ثمنه.</p>
     */
    public long daysRemaining(LocalDate today) {
        return tracked() ? ChronoUnit.DAYS.between(today, paidThrough) : 0;
    }

    /** أقاربَ الانقضاءُ بما يستدعي مطالبة؟ منقضٍ أو داخلٌ في نافذة السماح */
    public boolean needsChasing(LocalDate today) {
        return tracked() && daysRemaining(today) <= GRACE_DAYS;
    }

    /**
     * التاريخ بعد دفع عددٍ من الشهور.
     *
     * <p><b>من الأبعد بين اليوم وما هو مدفوع.</b> والاتجاهان خاطئان صامتاً:</p>
     *
     * <ul>
     *   <li>من <b>اليوم</b> دائماً يسرق أيامَ من دفع مبكراً - سنترٌ باقٍ له عشرة أيام
     *       يدفع شهراً فيحصل على ثلاثين لا أربعين.</li>
     *   <li>من <b>{@code paidThrough}</b> دائماً يشتري لسنترٍ منقضٍ منذ ثلاثة أشهر
     *       شهراً مضى: يبقى مغلقاً بعد أن دفع، والمشغّلُ يراه في قائمته دافعاً.</li>
     * </ul>
     *
     * <p>والاشتراك شهريٌّ ثابت، فالوحدة شهرٌ كامل لا يوم: {@code plusMonths} يقصر يومَ
     * 31 إلى آخر الشهر الأقصر من تلقاء نفسه، فسنترٌ دفع في 31 يناير ينتهي في 28 فبراير
     * ثم في 28 مارس - وذلك أهونُ من أن يزحف تاريخُه إلى الوراء شهراً بعد شهر.</p>
     */
    public LocalDate extendedBy(int months, LocalDate today) {
        if (months <= 0) {
            throw new IllegalArgumentException("a subscription is extended by whole months: " + months);
        }
        LocalDate from = paidThrough == null || paidThrough.isBefore(today) ? today : paidThrough;
        return from.plusMonths(months);
    }
}
