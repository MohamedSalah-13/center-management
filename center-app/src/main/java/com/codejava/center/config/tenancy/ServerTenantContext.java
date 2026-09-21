package com.codejava.center.config.tenancy;

import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
 *
 * <h2>واسمُ المؤسسة يُوسَم على كل سطر سجلّ من هنا</h2>
 *
 * <p>لأن هذا هو المصفاة: الطلب يدخل عبر {@code TenantBindingFilter} والدورةُ الليلية
 * عبر {@link #sweep}، وكلاهما ينتهي إلى {@link #call}. فسطرٌ واحد هنا يسم كل ما يُكتب
 * بعده - في الخدمات، وفي Hibernate، وفي Spring - بالمؤسسة التي يعمل لها الخيط، بلا
 * أن يمرّر أحدٌ المؤسسةَ إلى مسجِّل.</p>
 *
 * <p>وبدونه "فشلت النسخة الاحتياطية" في سجلّ خادمٍ يخدم خمسين سنتراً ليست معلومة:
 * تقول إن شيئاً وقع ولا تقول لمن، ولا سبيل إلى معرفته إلا بقراءة ما قبله وما بعده
 * أملاً في سطرٍ يذكر اسماً. {@code sweep} وحده كان يذكر الاسم، وذلك يغطّي فشلَ الدورة
 * كاملةً لا سطراً داخلها.</p>
 *
 * <p>والوسمُ <b>يُعاد إلى ما كان</b> لا يُمحى، للسبب الذي يُعاد له {@code CURRENT}
 * نفسه: النطاقات تتداخل - دورةٌ ليلية تستدعي عملاً داخلها - ومحوُه يترك ما بعد العمل
 * الداخلي بلا اسمٍ في السجلّ بينما هو ما زال داخل مؤسسة.</p>
 */
public class ServerTenantContext implements TenantContext, TenantSweep {

    private static final Logger log = LoggerFactory.getLogger(ServerTenantContext.class);

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    /**
     * مفتاح المؤسسة في سياق السجلّ.
     *
     * <p>ونمطُ السجلّ في {@code center-web/application.properties} يقرؤه بهذا الاسم
     * نفسه ({@code %X{centre}})، فتغييرُه هنا وحده يُسكت الوسمَ بلا أن يفشل شيء.</p>
     */
    public static final String LOG_KEY = "centre";

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
        String previousLabel = MDC.get(LOG_KEY);
        CURRENT.set(tenant);
        MDC.put(LOG_KEY, label(tenant));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            // والوسمُ يتبع النطاق لا العكس: مَحوُه هنا يترك خيطاً ما زال داخل مؤسسة
            // يكتب أسطراً بلا اسم، وهو ما يقع في كل عملٍ متداخل
            if (previousLabel == null) {
                MDC.remove(LOG_KEY);
            } else {
                MDC.put(LOG_KEY, previousLabel);
            }
        }
    }

    /**
     * ما يُكتب في السجلّ عن هذه المؤسسة: اسمُها المختصر لا رقمُها.
     *
     * <p>الرقمُ صحيحٌ ولا يُقرأ: من ينظر في سجلٍّ ليلاً يعرف السنتر باسمه، ورقمٌ يعني
     * استعلاماً ثانياً في جدولٍ آخر قبل أن يفهم السطر. والسجلّ خريطةٌ في الذاكرة
     * تُستبدل دفعةً واحدة، فالقراءة هنا ليست استعلاماً في كل طلب.</p>
     *
     * <p>ومعرّفٌ لا يعرفه السجلّ يُكتب برقمه مسبوقاً بـ {@code #}: هو حالةٌ تستحقّ
     * أن تُرى في السجلّ - جلسةٌ تشير إلى مؤسسة حُذفت - لا أن تُخفى خلف فراغ.</p>
     *
     * <p><b>ولا شيء هنا يُسقط عملاً.</b> الوسمُ زينةٌ على سطر سجلّ، ولا يصحّ أن يقرّر
     * هو نجاحَ طلبٍ أو فشلَه: سجلٌّ غائب - كما في اختبارٍ يبني النطاق وحده - أو قراءةٌ
     * تعثّرت، كلاهما يعني سطراً باسمٍ أقلّ وضوحاً لا طلباً مرفوضاً. نفسُ قاعدة
     * {@code AlertEngine.raise} و{@code prune}: ما كان طرفياً لا يحلّ محلّ الأصل.</p>
     */
    private String label(TenantId tenant) {
        if (registry != null) {
            try {
                String slug = registry.find(tenant).map(PlatformTenant::slug).orElse(null);
                if (slug != null) {
                    return slug;
                }
            } catch (RuntimeException ignored) {
                // يسقط إلى الرقم أدناه
            }
        }
        return "#" + tenant.value();
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
