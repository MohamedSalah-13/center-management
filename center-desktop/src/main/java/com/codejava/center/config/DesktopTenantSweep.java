package com.codejava.center.config;

import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.util.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * دورةُ المهام التلقائية على هذا الجهاز: سنترٌ واحد، فالعمل يُنفَّذ كما هو.
 *
 * <p>بلا حلقة وبلا سياقٍ يُضبط ويُعاد، وبلا عزلٍ للفشل. الثلاثة تخصّ من يخدم أكثر من
 * واحد. والأهمّ أن الاستثناء يصعد إلى المستدعي كما كان قبل تعدّد المؤسسات: تنبيه
 * {@code BACKUP_FAILED} مبنيٌّ على أن {@code BackupScheduler} يرى فشل نسخته، وتنفيذٌ
 * يبتلع الأخطاء "احتياطاً" كان سيُسكته بلا أن يلاحظ أحد.</p>
 *
 * <p>والمستأجر يُقرأ من {@link UserSession} لا من ثابت: هي الموضع الوحيد المسموح له
 * أن يعرف أن مستأجر سطح المكتب هو {@link TenantId#DESKTOP}.</p>
 */
@Component
@RequiredArgsConstructor
public class DesktopTenantSweep implements TenantSweep {

    private final UserSession userSession;

    @Override
    public void sweep(Consumer<TenantId> work) {
        work.accept(userSession.currentTenant());
    }

    @Override
    public void within(TenantId tenant, Runnable work) {
        work.run();
    }
}
