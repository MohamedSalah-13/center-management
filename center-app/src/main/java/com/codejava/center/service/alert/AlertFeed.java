package com.codejava.center.service.alert;

import com.codejava.center.domain.Alert;
import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.core.ui.UiDispatcher;
import com.codejava.center.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * مصدر الإشعارات المنبثقة: يقرأ ما استُجدّ من التنبيهات ويسلّمه للشاشة على خيط الواجهة.
 *
 * <h2>مساران يلتقيان في قراءة واحدة</h2>
 *
 * <p>التنبيه المولود على هذا الجهاز يصل عبر {@link AlertRaisedEvent} فوراً، والمولود
 * على جهاز آخر في السنتر يصل في النبضة الدورية. لكن <b>كليهما يمرّ من
 * {@link #poll()}</b>: الحدث لا يحمل التنبيه بل يقول "اقرأ الآن" فحسب.</p>
 *
 * <p>لو حمل الحدث الصفَّ جاهزاً لَصار للعرض مساران، ولَاحتاج كلٌّ منهما حاجزَه ضدّ
 * التكرار، ولَظهر التنبيه مرتين كلما تسابق الحدث والنبضة على نفس الصف - وهو سباق يقع
 * بالضبط حين يُطلق فحصٌ يدويّ دفعةً كبيرة. مسارٌ واحد وحاجزٌ واحد
 * ({@link #lastSeenId}) لا يمكن أن يخرج منه شيء مرتين.</p>
 *
 * <h2>لماذا بالمعرّف لا بالوقت</h2>
 *
 * <p>{@link #lastSeenId} عدّاد يمنحه الخادم، وساعات أجهزة السنتر لا تتطابق. مقارنةٌ
 * بـ {@code raisedAt} على جهاز ساعته متقدمة بدقيقة تتخطّى تنبيهات كتبها جاره فعلاً،
 * ولا يظهر الخطأ إلا كتنبيهات "ضاعت" لا يعرف أحد أنها كانت هناك.</p>
 *
 * <h2>الالتصاق والانفصال</h2>
 *
 * <p>الشاشة تُسجّل نفسها بـ {@link #attach} وتنسحب بـ {@link #detach}.
 * {@code DashboardController} نموذج ({@code PROTOTYPE}) ويُعاد بناؤه مع كل تبديل
 * للغة، فبقاء المستقبِل القديم يعني تسليم بطاقات إلى عقد JavaFX هُجرت نافذتها. ولذلك
 * أيضاً يُنفصل عند تسجيل الخروج: بطاقة تنبيه عن أرصدة الطلاب تقفز فوق شاشة الدخول
 * أمام من لم يسجّل دخوله بعد.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertFeed {

    private static final Logger log = LoggerFactory.getLogger(AlertFeed.class);

    /**
     * كل دقيقتين. الغرض منها التقاط ما فحصه جهازٌ آخر، والفحص المجدول يقع مرة في
     * اليوم: أقصر من ذلك استعلامان زائدان بلا فائدة، وأطول يجعل موظفاً يعالج تنبيهاً
     * عالجه زميله توّاً.
     */
    private static final Duration POLL_INTERVAL = Duration.ofMinutes(2);

    /**
     * سقف ما يُسلَّم في نبضة واحدة. فحصٌ يدويّ قد يولّد مئتَي تنبيه دفعةً واحدة،
     * ومئتا بطاقة منبثقة تشلّ الواجهة وتُغرق المستخدم. الباقي يبقى في الصندوق -
     * وهو المكان الذي يُقرأ فيه مئتا تنبيه أصلاً - والعدّاد يقول إنه هناك.
     */
    private static final int MAX_PER_POLL = 15;

    private final AlertRepository alertRepository;
    private final TaskScheduler taskScheduler;

    /**
     * القفزة إلى حيث تُمسّ الشاشة، محقونةً لا مكتوبةً هنا.
     *
     * <p>كانت حقلاً قيمته {@code Platform::runLater}، أي أن هذا الصنف يفترض أن خلفه
     * نافذةً وأدوات JavaFX مُقلعة — وهي ليست كذلك على خادم بلا شاشة ولا في خادم البناء.
     * Desktop يركّب خيط JavaFX والخادم يركّب ما يناسب SSE، وما بينهما — الحساب الذي لو
     * أخطأ لَظهر التنبيه مرتين أو لم يظهر أصلاً — واحدٌ يُختبر بلا نافذة، تماماً كما
     * جُرِّد {@code Printing.pageBreaks} من JavaFX للسبب نفسه.</p>
     */
    private final UiDispatcher uiThread;

    /**
     * أيّ مؤسسة تُقرأ تنبيهاتها، وكيف يُدخل إلى سياقها من خيط النبضة.
     */
    private final TenantContext tenantContext;

    private final TenantSweep tenants;

    /**
     * مستقبِلٌ لكل مؤسسة، ولكلٍّ عدّاده.
     *
     * <p>كان الاثنان حقلين مفردين، وهو صحيح ما دام البرنامج يخدم سنتراً واحداً أمام
     * إنسان واحد. على خادمٍ يجعل ذلك بطاقةَ تنبيهٍ عن رصيد طالبٍ في سنتر تقفز على شاشة
     * سنترٍ آخر - وهي بيانات صحيحة تماماً معروضة لمن لا يملكها، وهو أسوأ أشكال
     * الخلل لأن لا شيء فيه يبدو معطوباً.</p>
     *
     * <p>و<b>المفتاح مؤسسةٌ اليوم وسيصير مؤسسةً وجلسة</b> يوم تكون هناك جلسات: على
     * الجهاز شاشةٌ واحدة لكل برنامج فلا معنى لمفتاح ثانٍ، وعلى الويب لكل متصفّح
     * عدّاده. وحالةُ كل مراقِب معزولةٌ هنا أصلاً، فإضافة المفتاح الثاني تغييرُ مفتاح
     * لا إعادةُ بناء.</p>
     */
    private final Map<TenantId, Watcher> watchers = new ConcurrentHashMap<>();

    private ScheduledFuture<?> polling;

    /** شاشةٌ تراقب: أين تُسلَّم التنبيهات، وإلى أين وصلت قراءتها */
    private record Watcher(Consumer<AlertBatch> sink, AtomicLong lastSeenId) {
    }

    /**
     * تسجيل شاشة لاستقبال التنبيهات، وبدء النبض.
     *
     * <p><b>تُستدعى من خيط خلفي لا من خيط الواجهة:</b> أول ما تفعله سؤال قاعدة
     * البيانات عن أكبر معرّف.</p>
     *
     * <p>وذلك السؤال هو خط البداية: بدونه ينهال على المستخدم عند كل تسجيل دخول كل ما
     * تراكم منذ أسبوع دفعةً واحدة، وهي تنبيهات رآها بالأمس. ما قبل هذه اللحظة مكانه
     * الصندوق لا بطاقة تقفز في الزاوية.</p>
     */
    public synchronized void attach(Consumer<AlertBatch> newSink) {
        TenantId tenant = tenantContext.currentTenant();
        watchers.put(tenant, new Watcher(newSink, new AtomicLong(alertRepository.findHighestId())));

        if (polling == null) {
            polling = taskScheduler.scheduleWithFixedDelay(this::pollWatched,
                    Instant.now().plus(POLL_INTERVAL), POLL_INTERVAL);
        }
    }

    /**
     * انسحاب الشاشة: عند تسجيل الخروج، وقبل إعادة بناء لوحة القيادة.
     *
     * <p>والنبضة تتوقف حين لا يبقى مراقِبٌ واحد، لا حين ينسحب أوّل واحد: على الجهاز لا
     * فرق لأن المراقِب واحد، وعلى خادمٍ إيقافُها لأن سنتراً سجّل خروجه يُسكت تنبيهات
     * كل من بقي.</p>
     */
    public synchronized void detach() {
        watchers.remove(tenantContext.currentTenant());

        if (watchers.isEmpty() && polling != null) {
            polling.cancel(false);
            polling = null;
        }
    }

    /**
     * تنبيه جديد كُتب على هذا الجهاز: اقرأ الآن بدل انتظار النبضة.
     *
     * <p>القراءة تُحوَّل إلى خيط المجدوِل ولا تقع على الخيط الناشر: الحدث يُنشر من داخل
     * الفحص - وقد يكون خيط الواجهة حين يضغط المدير "فحص الآن" - واستعلامان إضافيان
     * هناك يُجمّدان الشاشة للحظة بلا سبب.</p>
     */
    @EventListener(AlertRaisedEvent.class)
    public void onAlertRaised() {
        TenantId tenant = tenantContext.currentTenant();
        if (watchers.containsKey(tenant)) {
            taskScheduler.schedule(() -> tenants.within(tenant, () -> poll(tenant)), Instant.now());
        }
    }

    /**
     * النبضة الدورية: مؤقّتٌ واحد يمرّ على المؤسسات <b>المُراقَبة</b> لا المخدومة.
     *
     * <p>مئة سنترٍ مشترك لا يعني مئة شاشة مفتوحة؛ القراءة لمن أمامه أحد.</p>
     */
    private void pollWatched() {
        for (TenantId tenant : watchers.keySet()) {
            tenants.within(tenant, () -> poll(tenant));
        }
    }

    /**
     * قراءة واحدة، وتسليم واحد.
     *
     * <p>{@code synchronized} لأن النبضة الدورية وحدث تنبيه جديد قد يلتقيان على خيطين:
     * بلا القفل يقرأ الاثنان نفس {@link #lastSeenId} فيُسلَّم الصف مرتين، وهو بالضبط ما
     * يقع لحظة الفحص اليدوي.</p>
     *
     * <p>ويُسلَّم ولو لم يستجدّ شيء: العدّاد يتغيّر أيضاً حين يعالج زميلٌ تنبيهاً على
     * جهازه، ورقمٌ عالق على قيمة الأمس أسوأ من رقم متأخر بدقيقتين.</p>
     */
    synchronized void poll(TenantId tenant) {
        Watcher watcher = watchers.get(tenant);
        if (watcher == null) {
            return;
        }

        try {
            List<Alert> fresh = alertRepository.findRaisedAfter(
                    watcher.lastSeenId().get(), PageRequest.of(0, MAX_PER_POLL));

            if (!fresh.isEmpty()) {
                watcher.lastSeenId().set(fresh.get(fresh.size() - 1).getId());
            }

            deliver(watcher, new AlertBatch(fresh, alertRepository.countByAcknowledgedAtIsNull()));
        } catch (RuntimeException e) {
            // قاعدة بيانات مقطوعة لحظياً: النبضة التالية تصلح الأمر، ونافذة خطأ كل
            // دقيقتين فوق شاشة يعمل عليها أحد أسوأ من عدّاد متأخر
            log.warn("تعذّرت قراءة التنبيهات الجديدة: {}", e.getMessage());
        }
    }

    /**
     * التسليم على خيط الواجهة. تُقرأ {@link #sink} داخل {@code runLater} لا خارجه:
     * قد تكون الشاشة انسحبت بين جدولة التسليم وتنفيذه - تسجيل خروج، أو تبديل لغة -
     * والتسليم إلى مستقبِل مهجور يبني بطاقة فوق نافذة لم تعد موجودة.
     */
    private void deliver(Watcher watcher, AlertBatch batch) {
        uiThread.dispatch(() -> {
            // تُقرأ الخريطة داخل المهمة لا خارجها: قد تكون الشاشة انسحبت بين الجدولة
            // والتنفيذ، والتسليم إلى مستقبِل مهجور يبني بطاقة فوق نافذة لم تعد موجودة
            if (watchers.containsValue(watcher)) {
                watcher.sink().accept(batch);
            }
        });
    }
}
