package com.codejava.center.config.tenancy;

import com.codejava.center.platform.TenantRegistry;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * يترجم "أيّ مؤسسة" إلى "أيّ قاعدة" عند فتح كل جلسة Hibernate.
 *
 * <p>الترجمة في موضع واحد لأن الطرفين يختلفان في طبيعتهما: {@link
 * com.codejava.center.core.tenant.TenantId} رقمٌ ثابت لا يتغيّر ما دامت المؤسسة قائمة،
 * واسمُ القاعدة نصٌّ قد يُنقل يوماً إلى خادم آخر أو يُعاد تسميته. ربطُ الخدمات بالاسم
 * يجعل كل نقلٍ تغييراً في البيانات المخزّنة.</p>
 *
 * <p><b>ولا قاعدة افتراضية عند غياب المؤسسة - لكنّ الرفض أُزيح خطوةً واحدة.</b>
 * كان هذا الصنف يُسقط العمل هنا، وهو ما يمنع الخادم من الإقلاع أصلاً: Spring Data
 * يفتح جلسةً واحدة عند بناء كل مستودع ليعرف أيّ مزوّد JPA يعمل خلفه، وHibernate يسأل
 * عن المؤسسة كلما فُتحت جلسة - فيسقط الإقلاع على خيطٍ لا مؤسسة له بطبيعته. ولم يظهر
 * ذلك محلياً لأن الاختبار الذي يُقلع بتعدّد المؤسسات يحتاج Docker ولا يعمل بدونه.</p>
 *
 * <p>فالغياب يُجاب عنه الآن بمعرّفٍ <b>محجوز لا يقابل قاعدة</b>، و
 * {@link SchemaPerTenantConnectionProvider} يرفض أن يفتح به اتصالاً. جلسةٌ لا تُنفّذ
 * عبارة - كجلسة الإقلاع تلك - تمرّ، وأوّلُ عبارة تحتاج اتصالاً تسقط بالرسالة نفسها.
 * والقاعدة التي وُضعت لأجلها المرحلة كلها باقية بحرفها: <b>لا استعلام يقرأ قاعدةً ما
 * لأنه نُسي خارج نطاق مؤسسة</b> - بل صار الرفض في الاتصال، وهو الموضع الذي يقع فيه
 * التسرّب أصلاً: العزل في الاتصال لا في الاستعلامات.</p>
 */
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    /**
     * معرّفٌ لا يقابل قاعدةً، ولا يمكن أن يقابلها.
     *
     * <p>الشرطة فيه هي الضمانة: {@code SchemaName} لا تقبل إلا حرفاً صغيراً ثم حروفاً
     * وأرقاماً وشرطات سفلية، فلا سنتر يمكن أن يحمل هذا الاسم مهما سُمّي.</p>
     */
    public static final String NO_TENANT = "no-tenant";

    /**
     * {@code ServerTenantContext} لا {@code TenantContext}: هذا الصنف لا يوجد إلا على
     * المسار الذي يوجد فيه ذاك، والسؤال المطلوب هنا - "هل ثمّة مؤسسة" - لا تجيب عنه
     * الواجهة إلا باستثناءٍ يُلتقط، وحالةُ الإقلاع ليست خطأً ليُصنع لها استثناء.
     */
    private final ServerTenantContext tenantContext;

    private final TenantRegistry registry;

    public TenantSchemaResolver(ServerTenantContext tenantContext, TenantRegistry registry) {
        this.tenantContext = tenantContext;
        this.registry = registry;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (!tenantContext.isBound()) {
            return NO_TENANT;
        }
        return registry.schemaOf(tenantContext.currentTenant()).value();
    }

    /**
     * {@code true}: جلسةٌ محفوظة على الخيط من عملٍ سابق قد تكون لمؤسسة أخرى، ويجب أن
     * يتحقّق Hibernate من ذلك بدل أن يُعيد استعمالها.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
