package com.codejava.center.web.api;

import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.domain.Alert;
import com.codejava.center.service.alert.AlertBatch;
import com.codejava.center.service.alert.AlertFeed;
import com.codejava.center.web.CentreAuthentication;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * التنبيهات إلى المتصفّح، مجرىً مفتوحاً.
 *
 * <p>هذا ما يقابل الجرسَ والبطاقةَ المنبثقة على الجهاز، ومصدرُه واحد:
 * {@link AlertFeed} - القراءةُ نفسها، وحاجزُ التكرار نفسه ({@code lastSeenId})،
 * والنبضةُ نفسها التي تلتقط ما كتبه مجدوِلٌ آخر. لا يُقرأ هنا صفٌّ واحد من
 * {@code alerts}.</p>
 *
 * <p>و<b>SSE لا WebSocket</b>: الاتجاه واحد - الخادم يقول والمتصفّح يسمع - وSSE
 * نصٌّ على HTTP عادي، يمرّ من أيّ وسيط ويعيد الاتصال بنفسه حين ينقطع. WebSocket
 * بروتوكولٌ ثانٍ ومصافحةُ ترقية لأجل اتجاهٍ لا يُستعمل.</p>
 *
 * <h2>مستقبِلٌ واحد لكل سنتر، ومجارٍ كثيرة تحته</h2>
 *
 * <p>{@code AlertFeed} يحمل مستقبِلاً واحداً لكل مؤسسة - وهو الصواب على جهازٍ فيه
 * شاشة واحدة. وعلى الويب لسنترٍ واحد عشرةُ متصفّحات مفتوحة، فأوّل من يشترك يسجّل
 * المستقبِل وآخرُ من ينصرف يسحبه، وما بينهما توزيعٌ على القائمة. ولو سجّل كلٌّ
 * مستقبِله لَأزاح سابقه - {@code attach} تكتب فوق ما في الخريطة - فيصمت الجميع إلا
 * آخرَ من فتح الشاشة.</p>
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertStreamController {

    private static final Logger log = LoggerFactory.getLogger(AlertStreamController.class);

    /**
     * ساعةٌ ثم يُغلق المجرى ويعيد المتصفّح فتحه.
     *
     * <p>مجرىً بلا أجل يُقطع في النهاية من وسيطٍ في الطريق - ويُقطع بصمت: الخادم
     * يظنّه مفتوحاً ويكتب فيه، والمتصفّح لا يعرف أنه انتهى فلا يعيد الفتح.</p>
     */
    private static final long STREAM_TIMEOUT = Duration.ofHours(1).toMillis();

    private final AlertFeed alertFeed;
    private final TenantSweep tenants;

    private final Map<TenantId, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public record AlertView(Long id, String type, String severity, String category,
                            String text, String entityLabel, LocalDateTime raisedAt) {
    }

    public record BatchView(List<AlertView> fresh, long openCount) {
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        TenantId tenant = currentTenant();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT);

        List<SseEmitter> forTenant = listeners.computeIfAbsent(tenant,
                ignored -> new CopyOnWriteArrayList<>());

        synchronized (forTenant) {
            forTenant.add(emitter);
            if (forTenant.size() == 1) {
                alertFeed.attach(batch -> fanOut(tenant, batch));
            }
        }

        // الثلاثة تقع على خيطٍ بلا مؤسسة - الحاوية تُنهي المجرى بعد أن ينتهي الطلب -
        // فالمعرّف يُمرَّر معها بدل أن يُقرأ من سياق لم يعد موجوداً
        emitter.onCompletion(() -> remove(tenant, emitter));
        emitter.onTimeout(() -> remove(tenant, emitter));
        emitter.onError(error -> remove(tenant, emitter));

        return emitter;
    }

    /**
     * التوزيع على متصفّحات هذا السنتر وحده.
     *
     * <p>{@code CopyOnWriteArrayList} لأن الكتابة تقع على خيط النبضة بينما يشترك
     * متصفّحٌ جديد أو ينصرف آخر على خيط طلب.</p>
     */
    private void fanOut(TenantId tenant, AlertBatch batch) {
        BatchView view = view(batch);
        for (SseEmitter emitter : listeners.getOrDefault(tenant, List.of())) {
            try {
                emitter.send(SseEmitter.event().name("alerts").data(view));
            } catch (IOException | IllegalStateException closed) {
                // تبويبٌ أُغلق قبل أن تصل onCompletion: يُنظَّف هنا، ولا يُسقط
                // التوزيع على البقية
                remove(tenant, emitter);
            }
        }
    }

    private void remove(TenantId tenant, SseEmitter emitter) {
        List<SseEmitter> forTenant = listeners.get(tenant);
        if (forTenant == null) {
            return;
        }
        synchronized (forTenant) {
            forTenant.remove(emitter);
            if (forTenant.isEmpty()) {
                listeners.remove(tenant, forTenant);
                // داخل نطاق المؤسسة: detach تقرأ المؤسسة من السياق، والخيط هنا
                // خيطُ إنهاءٍ لا خيطُ طلب
                try {
                    tenants.within(tenant, alertFeed::detach);
                } catch (RuntimeException e) {
                    log.warn("تعذّر فكّ مراقبة التنبيهات: {}", e.getMessage());
                }
            }
        }
    }

    private static BatchView view(AlertBatch batch) {
        return new BatchView(batch.fresh().stream().map(AlertStreamController::view).toList(),
                batch.openCount());
    }

    /**
     * الجملة تُبنى هنا لا تُقرأ من صف.
     *
     * <p>{@code alerts} يحمل النوع ووسائطه بلا لغة - نفس قاعدة سجل المراقبة - و
     * {@code describe()} يركّبها بلغة الطلب. ولو خُزّنت الجملة لَتجمّدت على لغة من
     * كتبها، وهو ما يعني على خادمٍ لغةَ المجدوِل لا لغةَ القارئ.</p>
     */
    private static AlertView view(Alert alert) {
        return new AlertView(alert.getId(), alert.getType().name(),
                alert.getSeverity().name(), alert.getType().getCategory().name(),
                alert.describe(), alert.getEntityLabel(), alert.getRaisedAt());
    }

    private TenantId currentTenant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof CentreAuthentication centre) {
            return centre.tenant();
        }
        throw new IllegalStateException("no centre bound to this request");
    }
}
