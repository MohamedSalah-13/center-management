package com.codejava.center.web;

import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.enums.Currency;
import com.codejava.center.service.SettingsChangedEvent;
import com.codejava.center.service.SettingsService;
import com.codejava.center.util.CurrencyProvider;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * عملة <b>مؤسسة هذا الطلب</b>: جوابُ الخادم عن {@code CurrencyProvider}.
 *
 * <p>{@code DesktopCurrencyProvider} يقرأ الصفّ مرةً عند الإقلاع ويحتفظ بقيمةٍ واحدة،
 * وذلك صحيحٌ حيث يخدم البرنامج سنتراً واحداً. وحقلٌ واحد هنا يعني أن آخر سنترٍ حفظ
 * إعداداته يطبع رمز عملته على أرقام كل السناتر - وكلُّ رقمٍ منها صحيح، وهو ما يجعل
 * الخلل غير مرئي حتى يصل إيصالٌ بالريال إلى سنترٍ مصري.</p>
 *
 * <p>ولهذا <b>المزوّد هو من يملك الذاكرة المؤقتة لا {@code MoneyUtils}</b>: التنفيذ
 * وحده يعرف بأيّ مفتاح تُخزَّن. {@code formatWithCurrency} تُستدعى لكل خلية في جدول
 * ولكل سطر في تقرير، فقراءةٌ للقاعدة داخلها تعني استعلاماً لكل سطر على الشاشة.</p>
 *
 * <p>والقراءة كسولة لا عند الإقلاع: الخادم لا يعرف مؤسساته وقت الإقلاع بالضرورة،
 * وقراءةُ مئة صفّ إعدادات قبل أول طلب تؤخّر الإقلاع لأجل قيمةٍ قد لا تُطلب.</p>
 */
@Component
@RequiredArgsConstructor
public class ServerCurrencyProvider implements CurrencyProvider {

    private final SettingsService settingsService;

    private final CurrentCentre centre;

    private final Map<TenantId, Currency> byTenant = new ConcurrentHashMap<>();

    /**
     * {@code null} حين لا مؤسسة على الخيط، ويقرؤها {@code MoneyUtils} على أنها
     * "لا عملة مخزَّنة" فتعود إلى {@link Currency#DEFAULT}.
     *
     * <p>الرمزُ بجوار الرقم ليس استعلاماً عن بيانات أحد، فلا يصحّ أن يُسقط الطلب
     * بخطأ "لا مؤسسة على هذا الخيط" في موضعٍ لا يقول شيئاً عن السبب.</p>
     */
    @Override
    public Currency current() {
        TenantId tenant = centre.boundOrNull();
        if (tenant == null) {
            return null;
        }
        return byTenant.computeIfAbsent(tenant, ignored -> settingsService.getSettings().getCurrency());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        MoneyUtils.install(this);
    }

    /**
     * بعد الالتزام، ولمؤسسةِ من حفظ وحدها: الحدث يصل على خيط الطلب وهو مربوطٌ
     * بمؤسسته، فالمفتاح معروف بلا أن يحمله الحدث.
     */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        TenantId tenant = centre.boundOrNull();
        if (tenant != null) {
            byTenant.put(tenant, event.settings().getCurrency());
        }
    }
}
