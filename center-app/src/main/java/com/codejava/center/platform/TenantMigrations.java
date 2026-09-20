package com.codejava.center.platform;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * ترقية مخطط كل مؤسسة عند الإقلاع.
 *
 * <p>على جهازٍ في سنتر يفعل Spring Boot هذا وحده: قاعدة واحدة، وFlyway عليها. وعلى
 * خادمٍ القواعد بعدد السناتر، فالترقية حلقة - ونسخةُ برنامجٍ جديدة تُنشر بينما
 * قواعد مئة سنتر ما تزال على المخطط القديم.</p>
 *
 * <p><b>وفشلُ مؤسسة لا يمنع الإقلاع ولا يمرّ بصمت.</b> ترحيلٌ يتعثّر في قاعدة واحدة -
 * صفٌّ يخالف قيداً جديداً مثلاً - لو أسقط الخادم لَحرم تسعاً وتسعين مؤسسة سليمة من
 * الخدمة بسبب واحدة. ولو مرّ بصمت لَخدم تلك الواحدة ببرنامجٍ يتوقّع مخططاً غير
 * الذي أمامه، وهو أسوأ: استعلامٌ يفشل عند أول شاشة بدل رسالة واضحة عند الإقلاع.
 * فالمخرج الثالث: تُسجَّل باسمها، وتُعاد في {@link #failures()} ليقرّر من ينشر.</p>
 */
public class TenantMigrations {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrations.class);

    private final DataSource dataSource;
    private final TenantRegistry registry;
    private final String migrationLocations;

    private final List<String> failures = new ArrayList<>();

    public TenantMigrations(DataSource dataSource, TenantRegistry registry, String migrationLocations) {
        this.dataSource = dataSource;
        this.registry = registry;
        this.migrationLocations = migrationLocations;
    }

    /** يُشغَّل مرة عند الإقلاع على كل مؤسسة لم تُغلق */
    public void migrateAll() {
        failures.clear();
        List<PlatformTenant> tenants = registry.migrated();
        log.info("ترقية مخطط {} مؤسسة", tenants.size());

        for (PlatformTenant tenant : tenants) {
            try {
                int applied = migrate(tenant);
                if (applied > 0) {
                    log.info("المؤسسة {}: طُبّق {} ترحيلاً", tenant.slug(), applied);
                }
            } catch (RuntimeException e) {
                failures.add(tenant.slug());
                log.error("فشلت ترقية مخطط المؤسسة {} ({}): {}",
                        tenant.slug(), tenant.schema(), e.getMessage(), e);
            }
        }

        if (!failures.isEmpty()) {
            log.error("مؤسسات لم يكتمل ترحيل مخططها وتعمل بمخطط قديم: {}", failures);
        }
    }

    /**
     * ترحيل قاعدة مؤسسة واحدة.
     *
     * <p>{@code schemas(...)} لا رابطُ JDBC يحمل اسم القاعدة: Flyway يضع جدول تاريخه
     * في القاعدة المذكورة، ومجمّعُ اتصالات واحدٌ يخدم الجميع. و{@code baselineOnMigrate}
     * مطفأ عن قصد - قاعدةٌ فيها جداول بلا تاريخ ترحيل ليست قاعدةً قديمة بل قاعدةٌ لا
     * نعرف مخططها، وتأسيسُها بصمت يبني فوق مجهول.</p>
     *
     * @return عدد الترحيلات المطبَّقة الآن
     */
    public int migrate(PlatformTenant tenant) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(tenant.schema().value())
                .defaultSchema(tenant.schema().value())
                .locations(migrationLocations)
                .baselineOnMigrate(false)
                .load()
                .migrate()
                .migrationsExecuted;
    }

    /** أسماء المؤسسات التي فشل ترحيلها في آخر دورة */
    public List<String> failures() {
        return List.copyOf(failures);
    }
}
