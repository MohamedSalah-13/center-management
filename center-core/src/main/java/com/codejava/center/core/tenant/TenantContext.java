package com.codejava.center.core.tenant;

/**
 * يحدد المؤسسة التي تعمل العملية داخلها.
 *
 * <p>يكون ثابتاً في Desktop المستقل، ومستخرجاً من هوية المستخدم لكل طلب في SaaS.
 * لا يجوز أن يأتي من قيمة يختارها العميل من دون التحقق من عضويته.</p>
 */
public interface TenantContext {

    TenantId currentTenant();
}
