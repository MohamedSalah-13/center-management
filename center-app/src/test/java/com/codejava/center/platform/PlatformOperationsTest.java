package com.codejava.center.platform;

import com.codejava.center.config.tenancy.ServerTenantContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حلقةُ المسح: من تشمل، وماذا تفعل حين لا يُقرأ سنتر.
 *
 * <p>والقراءةُ نفسها ليست هنا - هي تحتاج MySQL بمخطَّطٍ لكل سنتر وDocker، وتغطّيها
 * {@code MultiTenantIsolationIntegrationTest}. المقصودُ هنا سلوكُ الحلقة وحده، وهو
 * ما يخطئ صامتاً: قائمةٌ من تسعةٍ وأربعين تبدو سليمةً تماماً.</p>
 *
 * <p>ولذلك يحلّ الاختبارُ محلَّ {@code read} - نفسُ حجّة البديل عن مجمّع الاتصالات في
 * {@code SchemaRoutingTest}: العطلُ لا يظهر إلا حين تُقرأ عدةُ سنترات في دورةٍ
 * واحدة، وهو ما لا يقع في اختبارٍ يفتح واحداً.</p>
 */
class PlatformOperationsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    private static final Clock FIXED =
            Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private TenantRegistry registry;
    private ServerTenantContext context;

    @BeforeEach
    void registerThreeTenants() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ops_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
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

        registry = new TenantRegistry(platform);
        registry.refresh();
        context = new ServerTenantContext(registry);
    }

    /**
     * المسحُ يشمل الموقوف.
     *
     * <p>على عكس الدورة الليلية، التي تتخطّاه عمداً. والسبب مختلف: الدورة تسأل "لمن
     * أعمل"، والمسح يسأل "لماذا هذا مظلم" - وحذفُ الموقوف من الجواب يجعل غيابَه
     * لغزاً بدل أن يكون سطراً يقول "موقوف".</p>
     */
    @Test
    void theSurveyListsSuspendedCentresTooBecauseTheirSilenceNeedsExplaining() {
        List<CentreOperations> rows = survey(tenant -> readable(tenant));

        assertThat(rows).extracting(CentreOperations::slug)
                .containsExactly("cairo", "giza", "tanta");
        assertThat(rows).extracting(CentreOperations::status)
                .containsExactly(TenantStatus.ACTIVE, TenantStatus.SUSPENDED, TenantStatus.ACTIVE);
    }

    /**
     * <b>سنترٌ لا يُقرأ يبقى في القائمة موسوماً بسببه، ومن بعده يُقرأ.</b>
     *
     * <p>ابتلاعُ الفشل وإسقاطُ السطر يترك قائمةً تبدو تامّة، والغائبُ هو بالضبط الذي
     * كان ينبغي أن يُرى. والتوقّفُ عنده يترك من بعده بلا مسح - وهو نفسُ العطل الذي
     * يعزله {@code sweep} في الدورة الليلية.</p>
     */
    @Test
    void oneUnreadableCentreNeitherVanishesFromTheListNorStopsTheRest() {
        List<String> attempted = new ArrayList<>();

        List<CentreOperations> rows = survey(tenant -> {
            attempted.add(tenant.slug());
            if ("giza".equals(tenant.slug())) {
                throw new IllegalStateException("قاعدة هذا السنتر مقطوعة");
            }
            return readable(tenant);
        });

        assertThat(attempted)
                .as("والقراءةُ لا تتوقف عند الفاشل")
                .containsExactly("cairo", "giza", "tanta");

        assertThat(rows).extracting(CentreOperations::slug)
                .containsExactly("cairo", "giza", "tanta");

        CentreOperations giza = rows.get(1);
        assertThat(giza.readable()).isFalse();
        assertThat(giza.problem()).isEqualTo("قاعدة هذا السنتر مقطوعة");
        assertThat(giza.needsAttention()).isTrue();

        assertThat(rows.get(0).readable()).isTrue();
        assertThat(rows.get(2).readable()).isTrue();
    }

    /** وكلُّ سنترٍ يُقرأ داخل نطاقه هو: قراءةٌ خارجه تقرأ قاعدةَ غيره */
    @Test
    void everyCentreIsReadInsideItsOwnScope() {
        List<Long> seenFromInside = new ArrayList<>();

        survey(tenant -> {
            seenFromInside.add(context.currentTenant().value());
            return readable(tenant);
        });

        assertThat(seenFromInside).containsExactly(1L, 2L, 3L);
    }

    /** قراءةُ سنترٍ واحد، كما يفعل المسحُ الحقيقي داخل النطاق */
    private interface Reader {
        CentreOperations read(PlatformTenant tenant);
    }

    /**
     * مسحٌ بقارئٍ من الاختبار.
     *
     * <p>{@code settingsService} و{@code alertRepository} لا يُمرّران: القارئُ المستبدَل
     * لا يبلغهما، وتمريرُ نسخةٍ منهما يعني قاعدةَ بيانات لأجل اختبارٍ موضوعُه حلقةٌ
     * لا قاعدة.</p>
     */
    private List<CentreOperations> survey(Reader reader) {
        return new PlatformOperations(registry, context, null, null,
                FIXED) {
            @Override
            CentreOperations read(PlatformTenant tenant, LocalDateTime now) {
                // النطاق يُدخَل هنا كما يُدخَل في الأصل، فيبقى ما يُختبر هو الحلقة
                return context.call(tenant.id(), () -> reader.read(tenant));
            }
        }.survey();
    }

    private static CentreOperations readable(PlatformTenant tenant) {
        return new CentreOperations(tenant.id().value(), tenant.name(), tenant.slug(),
                tenant.status(), true, null,
                true, NOW.minusHours(1), false,
                true, NOW.minusHours(1), false,
                0);
    }

    private void insert(JdbcTemplate platform, long id, String slug, TenantStatus status) {
        platform.update("INSERT INTO tenants (id, name, slug, schema_name, status) VALUES (?, ?, ?, ?, ?)",
                id, "سنتر " + slug, slug, "center_" + slug, status.name());
    }
}
