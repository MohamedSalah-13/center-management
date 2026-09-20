package com.codejava.center.platform;

import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;

/**
 * صفٌّ من سجلّ المنصة: مؤسسة، وأين قاعدتها، وحال اشتراكها.
 *
 * <p>سجلّ ({@code record}) لا كيان JPA عن قصد. {@code EntityManagerFactory} في هذا
 * البرنامج موجَّهٌ إلى قاعدة المؤسسة الحالية، فكيانٌ يقرأ سجلّ المنصة عبره يعني إمّا
 * مصنعاً ثانياً بمدير معاملات ثانٍ - فيصير {@code @Transactional} في كل خدمة سؤالاً
 * "أيّ قاعدة؟" - أو جدولاً يُقرأ من قاعدةٍ ليست له. سجلّ المنصة أربعة استعلامات،
 * و{@code JdbcTemplate} عليها أصدق من مصنعٍ كامل.</p>
 */
public record PlatformTenant(TenantId id, String name, String slug, SchemaName schema,
                             TenantStatus status) {

    public PlatformTenant {
        if (id == null || schema == null || status == null) {
            throw new IllegalArgumentException("tenant id, schema and status are required");
        }
    }
}
