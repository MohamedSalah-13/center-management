package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.platform.TenantStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * نطاق المؤسسة على الخيط، ودورةُ المهام التلقائية عليها.
 *
 * <p>ثلاثة أعطال كلّها في <b>ما لا يحدث</b>، وهو ما يجعلها غير مرئية بلا اختبار:
 * عملٌ يقع بلا مؤسسة فيقرأ من قاعدةٍ ليست له؛ ونطاقٌ لا يُعاد بعد انتهاء العمل فيبقى
 * على خيطٍ من مجمّع يأخذه غيره؛ ودورةٌ تتوقف عند أول مؤسسة تفشل فتترك من بعدها بلا
 * نسخة ولا فحص.</p>
 */
class ServerTenantContextTest {

    private ServerTenantContext context;

    @BeforeEach
    void registerThreeTenants() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:platform_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        JdbcTemplate platform = new JdbcTemplate(dataSource);
        platform.execute("""
                CREATE TABLE tenants (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    slug VARCHAR(80) NOT NULL,
                    schema_name VARCHAR(48) NOT NULL,
                    status VARCHAR(20) NOT NULL)
                """);
        insert(platform, 1, "cairo", TenantStatus.ACTIVE);
        insert(platform, 2, "giza", TenantStatus.SUSPENDED);
        insert(platform, 3, "tanta", TenantStatus.ACTIVE);

        TenantRegistry registry = new TenantRegistry(platform);
        registry.refresh();
        context = new ServerTenantContext(registry);
    }

    /**
     * عملٌ بلا مؤسسة يُسقط نفسه ولا يقرأ من قاعدةٍ "افتراضية".
     *
     * <p>هذا هو الخيار الذي تقوم عليه المرحلة كلها: عطلٌ صاخب أهون من تسرّب صامت.</p>
     */
    @Test
    void workWithNoTenantFailsInsteadOfReadingSomebodysData() {
        assertThatThrownBy(context::currentTenant).isInstanceOf(IllegalStateException.class);
        assertThat(context.isBound()).isFalse();
    }

    @Test
    void theTenantIsVisibleInsideTheScopeAndGoneAfterIt() {
        TenantId cairo = new TenantId(1);

        context.within(cairo, () -> assertThat(context.currentTenant()).isEqualTo(cairo));

        assertThat(context.isBound())
                .as("خيطٌ من مجمّع يأخذه غيره بعد انتهاء العمل")
                .isFalse();
    }

    /** نطاقٌ داخل نطاق: العودة إلى ما كان لا المسح، وإلا فقد النطاقُ الخارجي مؤسسته */
    @Test
    void anInnerScopeRestoresTheOuterOneRatherThanClearingIt() {
        TenantId cairo = new TenantId(1);
        TenantId tanta = new TenantId(3);

        context.within(cairo, () -> {
            context.within(tanta, () -> assertThat(context.currentTenant()).isEqualTo(tanta));
            assertThat(context.currentTenant()).isEqualTo(cairo);
        });
    }

    /** الدورة على المخدومة وحدها: سنترٌ توقّف اشتراكه لا تُرسل رسائل باسمه */
    @Test
    void theSweepSkipsTenantsWhoseSubscriptionStopped() {
        List<Long> visited = new ArrayList<>();

        context.sweep(tenant -> visited.add(tenant.value()));

        assertThat(visited).containsExactly(1L, 3L);
    }

    @Test
    void theSweepRunsEachTenantInsideItsOwnScope() {
        List<Long> seenFromInside = new ArrayList<>();

        context.sweep(tenant -> seenFromInside.add(context.currentTenant().value()));

        assertThat(seenFromInside).containsExactly(1L, 3L);
    }

    /**
     * نسخةٌ ليلية تتوقف عند السنتر الأول تترك من بعده بلا نسخة، وأحدٌ لا يعلم حتى
     * تُطلب واحدة.
     */
    @Test
    void oneTenantsFailureDoesNotStopTheRest() {
        List<Long> visited = new ArrayList<>();

        context.sweep(tenant -> {
            visited.add(tenant.value());
            if (tenant.value() == 1L) {
                throw new IllegalStateException("قاعدة هذا السنتر مقطوعة");
            }
        });

        assertThat(visited).containsExactly(1L, 3L);
    }

    private void insert(JdbcTemplate platform, long id, String slug, TenantStatus status) {
        platform.update("INSERT INTO tenants (id, name, slug, schema_name, status) VALUES (?, ?, ?, ?, ?)",
                id, "سنتر " + slug, slug, "center_" + slug, status.name());
    }
}
