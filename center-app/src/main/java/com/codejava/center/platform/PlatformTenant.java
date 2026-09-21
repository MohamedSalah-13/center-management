package com.codejava.center.platform;

import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.Subscription;
import com.codejava.center.core.tenant.TenantId;

import java.time.LocalDate;

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
                             TenantStatus status, LocalDate paidThrough) {

    public PlatformTenant {
        if (id == null || schema == null || status == null) {
            throw new IllegalArgumentException("tenant id, schema and status are required");
        }
    }

    /** حالُ المال: محورٌ ثانٍ بجوار {@link #status}, لا حالةٌ رابعة فيه */
    public Subscription subscription() {
        return Subscription.of(paidThrough);
    }

    /**
     * أتُخدَم هذه المؤسسة اليوم؟
     *
     * <p>ويلزم المحوران معاً: أن يسمح المشغّل <b>وأن</b> تكون مدفوعة. و{@code today}
     * وسيطٌ لا قراءةٌ من الساعة هنا، لأن هذا سجلٌّ لا خدمة - ومن يسأل يملك ساعته
     * المحقونة، فيبقى الجوابُ قابلاً للتثبيت في اختبار.</p>
     */
    public boolean isServedOn(LocalDate today) {
        return status.isServed() && !subscription().lapsed(today);
    }
}
