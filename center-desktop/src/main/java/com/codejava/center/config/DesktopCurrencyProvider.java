package com.codejava.center.config;

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

/**
 * عملة السنتر على هذا الجهاز: جوابُ سطح المكتب عن سؤال {@link CurrencyProvider}.
 *
 * <p>كان هذا الصنف {@code CurrencyInitializer} في طبقة الأعمال، يُفهَم على أنه "جسر"
 * يوصل قيمةً من القاعدة إلى حقلٍ ساكن. وهو ليس جسراً بل <b>جواباً</b>، وجوابه صحيح
 * بشرطٍ واحد: أن يخدم البرنامجُ سنتراً واحداً. {@code getSettings()} بلا ذكر مؤسسة،
 * وقيمةٌ واحدة محفوظة، وقراءةٌ متعجّلة عند الإقلاع - ثلاثتها تفترض ذلك الشرط. وهو شرط
 * الجهاز لا شرط طبقة الأعمال، فانتقل معه.</p>
 *
 * <p>على خادمٍ يكون الجواب من {@code TenantContext}، والذاكرة المؤقتة خريطةً لكل مؤسسة
 * يسقط منها مفتاحُ من حفظ إعداداته وحده. لا شيء من ذلك هنا، ولا يجب: سنترٌ واحد لا
 * يحتاج مفتاحاً.</p>
 *
 * <p>والقراءة مرة لا عند كل مبلغ عن قصد: {@code formatWithCurrency} يُستدعى في كل خلية
 * جدول وكل سطر كشف، وقراءةٌ لكل واحد منها تعني استعلاماً لكل صفّ على الشاشة.</p>
 */
@Component
@RequiredArgsConstructor
public class DesktopCurrencyProvider implements CurrencyProvider {

    private final SettingsService settingsService;

    /**
     * {@code null} حتى تصل أول قراءة، ومعناها "لم تُقرأ بعد" لا "لا عملة":
     * {@code MoneyUtils} يحلّ الاثنتين إلى الجنيه، وهو الصواب في الحالتين.
     */
    private volatile Currency currency;

    @Override
    public Currency current() {
        return currency;
    }

    /** القراءة قبل التركيب: لا يُسأل هذا المصدر لحظةً وهو لم يقرأ بعد */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        currency = settingsService.getSettings().getCurrency();
        MoneyUtils.install(this);
    }

    /**
     * بعد الـ commit لا قبله - نفس قاعدة {@code BackupScheduler}: عملةٌ حُفظت في معاملة
     * رجعت تجعل الشاشة تعرض مبالغ بعملة لا وجود لها في القاعدة.
     */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        currency = event.settings().getCurrency();
    }
}
