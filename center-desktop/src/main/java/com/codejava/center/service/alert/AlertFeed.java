package com.codejava.center.service.alert;

import com.codejava.center.domain.Alert;
import com.codejava.center.repository.AlertRepository;
import javafx.application.Platform;
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

    private volatile Consumer<AlertBatch> sink;
    private final AtomicLong lastSeenId = new AtomicLong(0);
    private ScheduledFuture<?> polling;

    /**
     * القفزة إلى خيط الواجهة، حقلاً لا استدعاءً مباشراً.
     *
     * <p>{@code Platform.runLater} يتطلب أن تكون أدوات JavaFX مُقلعة، وهي ليست كذلك في
     * الاختبارات ولا في خادم البناء. والحقل هنا هو ما يجعل الحساب الذي لو أخطأ لَظهر
     * التنبيه مرتين - أو لم يظهر أصلاً - قابلاً للاختبار بلا نافذة، تماماً كما جُرِّد
     * {@code Printing.pageBreaks} من JavaFX للسبب نفسه.</p>
     */
    private volatile Consumer<Runnable> uiThread = Platform::runLater;

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
        this.sink = newSink;
        this.lastSeenId.set(alertRepository.findHighestId());

        if (polling == null) {
            polling = taskScheduler.scheduleWithFixedDelay(this::poll,
                    Instant.now().plus(POLL_INTERVAL), POLL_INTERVAL);
        }
    }

    /** انسحاب الشاشة: عند تسجيل الخروج، وقبل إعادة بناء لوحة القيادة */
    public synchronized void detach() {
        this.sink = null;

        if (polling != null) {
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
        if (sink != null) {
            taskScheduler.schedule(this::poll, Instant.now());
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
    synchronized void poll() {
        if (sink == null) {
            return;
        }

        try {
            List<Alert> fresh = alertRepository.findRaisedAfter(
                    lastSeenId.get(), PageRequest.of(0, MAX_PER_POLL));

            if (!fresh.isEmpty()) {
                lastSeenId.set(fresh.get(fresh.size() - 1).getId());
            }

            deliver(new AlertBatch(fresh, alertRepository.countByAcknowledgedAtIsNull()));
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
    private void deliver(AlertBatch batch) {
        uiThread.accept(() -> {
            Consumer<AlertBatch> current = sink;
            if (current != null) {
                current.accept(batch);
            }
        });
    }

    /** يستبدل القفزة إلى خيط الواجهة بتنفيذ مباشر - للاختبار وحده */
    void dispatchOn(Consumer<Runnable> executor) {
        this.uiThread = executor;
    }
}
