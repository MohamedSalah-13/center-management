package com.codejava.center.config.tenancy;

import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * أيّ مؤسسة يعمل هذا الخيط لأجلها: جوابُ الخادم عن {@link TenantContext}.
 *
 * <p>{@code ThreadLocal} لأن المدى هنا هو الطلب أو دورة المجدوِل، لا البرنامج.
 * وسطحُ المكتب يجيب عن السؤال نفسه بـ {@code UserSession} وقيمةٍ ثابتة، لأن الجهاز
 * أمام إنسان واحد يخدم سنتراً واحداً.</p>
 *
 * <p><b>ولا يوجد جواب افتراضي.</b> خيطٌ لم يُضبط له مستأجر يُسقط العمل برسالة صريحة،
 * ولا يقرأ من قاعدةٍ "افتراضية". السقوط الصامت هنا هو بالضبط التسرّب الذي وُجدت هذه
 * المرحلة كلها لمنعه: استعلامٌ نُسي خارج نطاق مؤسسة يقرأ بيانات سنترٍ ويعرضها لآخر،
 * ولا شيء في سجل ولا على شاشة يقول إن ذلك وقع. عطلٌ صاخب أهون من تسرّب صامت.</p>
 */
public class ServerTenantContext implements TenantContext, TenantSweep {

    private static final Logger log = LoggerFactory.getLogger(ServerTenantContext.class);

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private final TenantRegistry registry;

    public ServerTenantContext(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public TenantId currentTenant() {
        TenantId tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException(
                    "no tenant on this thread: every unit of work runs inside one centre");
        }
        return tenant;
    }

    /** أثمّة مؤسسة على هذا الخيط؟ لمن يحتاج السؤال بلا أن يُسقط العمل. */
    public boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * ينفّذ العمل داخل نطاق مؤسسة، ويعيد ما كان.
     *
     * <p>{@code finally} يعيد القيمة السابقة لا يمسحها: خيوط المجمّع يُعاد استعمالها،
     * ومسحٌ يترك نطاقاً خارجياً - دورةَ مجدوِل تستدعي عملاً متداخلاً - بلا مستأجر بعد
     * أن يعود التنفيذ إليها.</p>
     */
    public <T> T call(TenantId tenant, Supplier<T> work) {
        TenantId previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    @Override
    public void within(TenantId tenant, Runnable work) {
        call(tenant, () -> {
            work.run();
            return null;
        });
    }

    /**
     * العمل الدوري مرةً لكل مؤسسة تُخدَم.
     *
     * <p>الفشل معزولٌ لكل مؤسسة: نسخةٌ ليلية تتوقف عند السنتر الثالث تترك من بعده بلا
     * نسخة، وأحدٌ لا يعلم حتى تُطلب واحدة. ويُسجَّل باسم المؤسسة لا بعددها، لأن "فشلت
     * نسخة" في سجلّ مئة سنتر ليست معلومة.</p>
     */
    @Override
    public void sweep(Consumer<TenantId> work) {
        for (PlatformTenant tenant : registry.served()) {
            try {
                within(tenant.id(), () -> work.accept(tenant.id()));
            } catch (RuntimeException e) {
                log.error("فشلت الدورة للمؤسسة {} ({}): {}",
                        tenant.slug(), tenant.id().value(), e.getMessage(), e);
            }
        }
    }
}
