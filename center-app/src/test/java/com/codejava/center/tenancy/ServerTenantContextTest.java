package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.platform.TenantStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
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
        JdbcTemplate platform = platformOf("default");
        insert(platform, 1, "cairo", TenantStatus.ACTIVE);
        insert(platform, 2, "giza", TenantStatus.SUSPENDED);
        insert(platform, 3, "tanta", TenantStatus.ACTIVE);

        TenantRegistry registry = new TenantRegistry(platform, Clock.systemDefaultZone());
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

    /**
     * <b>والدورة تتخطّى المنقضي اشتراكُه كما تتخطّى الموقوف.</b>
     *
     * <p>وهما محوران لا واحد: هذا أوقفه المشغّل، وذاك أوقفه المال. والمقصود واحد -
     * خادمٌ يأخذ نسخةً ويرسل رسائل إلى أولياء أمور سنترٍ توقّف عن الدفع منذ شهرين
     * يعمل باسم ذلك السنتر وعلى فاتورته.</p>
     *
     * <p>ولا مجدوِلَ أوقفه: الانقضاء يُحسب هنا عند كل قراءة، فلا ليلةَ تمرّ بلا
     * دورةِ إيقافٍ يبقى فيها منقضٍ يُخدَم.</p>
     */
    @Test
    void theSweepSkipsACentreWhoseSubscriptionLapsedJustAsItSkipsASuspendedOne() {
        JdbcTemplate platform = platformOf("lapsed");
        insert(platform, 1, "cairo", TenantStatus.ACTIVE);
        insert(platform, 2, "giza", TenantStatus.ACTIVE,
                LocalDate.now().minusMonths(2));
        insert(platform, 3, "tanta", TenantStatus.ACTIVE,
                // داخل أيام السماح: دُفع متأخراً، والأبواب لم تُغلق بعد
                LocalDate.now().minusDays(1));

        TenantRegistry registry = new TenantRegistry(platform, Clock.systemDefaultZone());
        registry.refresh();

        List<Long> visited = new ArrayList<>();
        new ServerTenantContext(registry).sweep(tenant -> visited.add(tenant.value()));

        assertThat(visited)
                .as("والمتأخر داخل السماح يُخدَم: حوالةٌ تتأخر يوماً ليست انقطاعاً")
                .containsExactly(1L, 3L);
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

    /* ------------------------------------------------- وسمُ السنتر في السجلّ */

    /**
     * كلُّ سطر يُكتب داخل نطاق سنترٍ يحمل اسمه.
     *
     * <p>و"فشلت النسخة الاحتياطية" في سجلّ خادمٍ يخدم خمسين سنتراً بلا هذا الوسم تقول
     * إن شيئاً وقع ولا تقول لمن.</p>
     */
    @Test
    void everyLineWrittenInsideACentreCarriesItsName() {
        context.within(new TenantId(1), () ->
                assertThat(MDC.get(ServerTenantContext.LOG_KEY)).isEqualTo("cairo"));
    }

    /** وينتهي بانتهاء النطاق: خيطٌ من مجمّع يأخذه غيره، ووسمٌ باقٍ يَنسب سطورَه إليه */
    @Test
    void theNameIsGoneOnceTheScopeEnds() {
        context.within(new TenantId(1), () -> {
        });

        assertThat(MDC.get(ServerTenantContext.LOG_KEY)).isNull();
    }

    /** ونطاقٌ داخل نطاق يعيد الوسمَ الخارجي، كما يعيد المؤسسة نفسها */
    @Test
    void anInnerScopeRestoresTheOuterName() {
        context.within(new TenantId(1), () -> {
            context.within(new TenantId(3), () ->
                    assertThat(MDC.get(ServerTenantContext.LOG_KEY)).isEqualTo("tanta"));

            assertThat(MDC.get(ServerTenantContext.LOG_KEY))
                    .as("محوُ الوسم يترك بقيةَ العمل الخارجي بلا اسم وهو داخل مؤسسة")
                    .isEqualTo("cairo");
        });
    }

    /**
     * ومعرّفٌ لا يعرفه السجلّ يُكتب برقمه لا بفراغ.
     *
     * <p>جلسةٌ تشير إلى مؤسسة حُذفت حالةٌ تستحقّ أن تُرى في السجلّ، وفراغٌ مكانها يُقرأ
     * سطراً لا يخصّ أحداً.</p>
     */
    @Test
    void aTenantTheRegistryDoesNotKnowIsLoggedByItsNumber() {
        context.within(new TenantId(404), () ->
                assertThat(MDC.get(ServerTenantContext.LOG_KEY)).isEqualTo("#404"));
    }

    /**
     * <b>والوسمُ لا يُسقط عملاً أبداً.</b>
     *
     * <p>هو زينةٌ على سطر سجلّ، ولا يصحّ أن يقرّر نجاحَ طلبٍ أو فشلَه. وهذا ليس
     * افتراضاً: أولُ صياغةٍ له كسرت {@code TenantBindingFilterTest} كاملاً، إذ يبني
     * النطاقَ بلا سجلّ - والطلبُ الذي كان يمرّ صار يسقط بـ{@code NullPointerException}
     * لأجل اسمٍ في سطر. نفسُ قاعدة {@code AlertEngine.raise} و{@code prune}.</p>
     */
    @Test
    void aMissingRegistryCostsTheLogAName_notTheWork() {
        ServerTenantContext bare = new ServerTenantContext(null);
        List<String> ran = new ArrayList<>();

        bare.within(new TenantId(1), () -> ran.add(MDC.get(ServerTenantContext.LOG_KEY)));

        assertThat(ran).containsExactly("#1");
    }

    /** والدورة الليلية تمرّ بالمصفاة نفسها، فأسطرُها موسومةٌ بلا سطرٍ إضافي فيها */
    @Test
    void theNightlySweepStampsEachTenantTooSoOneFunnelCoversBoth() {
        List<String> stamped = new ArrayList<>();

        context.sweep(tenant -> stamped.add(MDC.get(ServerTenantContext.LOG_KEY)));

        assertThat(stamped).containsExactly("cairo", "tanta");
    }

    /** سجلُّ منصةٍ فارغ على H2، جديدٌ لكل اختبار */
    private JdbcTemplate platformOf(String label) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:platform_" + label + "_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        JdbcTemplate platform = new JdbcTemplate(dataSource);
        platform.execute("""
                CREATE TABLE tenants (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    slug VARCHAR(80) NOT NULL,
                    schema_name VARCHAR(48) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    paid_through DATE NULL)
                """);
        return platform;
    }

    private void insert(JdbcTemplate platform, long id, String slug, TenantStatus status) {
        // مدفوعٌ بعيداً ما لم يكن الاشتراك موضوعَ الاختبار: وإلا صار كلُّ نتيجةٍ
        // هنا محتملةَ السبب - أهي الحالة أم المال
        insert(platform, id, slug, status, LocalDate.now().plusYears(1));
    }

    private void insert(JdbcTemplate platform, long id, String slug, TenantStatus status,
                        LocalDate paidThrough) {
        platform.update("""
                INSERT INTO tenants (id, name, slug, schema_name, status, paid_through)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, "سنتر " + slug, slug, "center_" + slug, status.name(), paidThrough);
    }
}
