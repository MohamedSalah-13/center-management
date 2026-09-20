package com.codejava.center.config.tenancy;

import com.codejava.center.core.tenant.TenantContext;
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
 * <p>ولا قيمة افتراضية عند غياب المؤسسة: {@link TenantContext} يُسقط العمل برسالته،
 * وهو المقصود - جلسةٌ تُفتح خارج نطاق مؤسسة خطأٌ في البرنامج، وإسنادُها إلى قاعدةٍ ما
 * يحوّل الخطأ إلى بيانات تُقرأ من حيث لا يجب.</p>
 */
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenantContext tenantContext;
    private final TenantRegistry registry;

    public TenantSchemaResolver(TenantContext tenantContext, TenantRegistry registry) {
        this.tenantContext = tenantContext;
        this.registry = registry;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
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
