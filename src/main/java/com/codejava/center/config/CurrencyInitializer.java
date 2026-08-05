package com.codejava.center.config;

import com.codejava.center.service.SettingsChangedEvent;
import com.codejava.center.service.SettingsService;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * يوصل عملة السنتر المحفوظة في القاعدة إلى {@link MoneyUtils} الساكن.
 *
 * <p>{@code MoneyUtils} لا يُحقن فيه شيء - يستدعيه كل شيء من الـ enums إلى خيوط
 * ForkJoinPool - فلا سبيل له إلى قراءة الإعدادات بنفسه. هذا الصف هو الجسر: يقرأ مرة
 * عند الإقلاع، ثم بعد كل حفظ للإعدادات.</p>
 *
 * <p>القراءة مرة واحدة لا عند كل مبلغ عن قصد: {@code formatWithCurrency} يُستدعى في كل
 * خلية جدول وكل سطر تقرير، وقراءةٌ لكل واحد منها تعني استعلاماً لكل صفّ على الشاشة.</p>
 */
@Component
@RequiredArgsConstructor
public class CurrencyInitializer {

    private final SettingsService settingsService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        MoneyUtils.setCurrency(settingsService.getSettings().getCurrency());
    }

    /**
     * بعد الـ commit لا قبله - نفس قاعدة {@code BackupScheduler}: عملةٌ حُفظت في معاملة
     * رجعت تجعل الشاشة تعرض مبالغ بعملة لا وجود لها في القاعدة.
     */
    @TransactionalEventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        MoneyUtils.setCurrency(event.settings().getCurrency());
    }
}
