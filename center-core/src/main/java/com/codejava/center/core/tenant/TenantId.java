package com.codejava.center.core.tenant;

/** معرّف المؤسسة المشتركة في المنصة. */
public record TenantId(long value) {

    /** المؤسسة الوحيدة في قواعد بيانات Desktop القديمة والجديدة. */
    public static final TenantId DESKTOP = new TenantId(1L);

    public TenantId {
        if (value <= 0) {
            throw new IllegalArgumentException("tenant id must be positive");
        }
    }
}
