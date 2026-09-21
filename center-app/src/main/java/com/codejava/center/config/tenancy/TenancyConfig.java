package com.codejava.center.config.tenancy;

import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.platform.PlatformOperations;
import com.codejava.center.platform.TenantMigrations;
import com.codejava.center.platform.TenantProvisioning;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.service.InitialSetupService;
import com.codejava.center.service.SettingsService;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * تركيب تعدّد المؤسسات: قاعدة المنصة، وموجِّه الاتصال، ونطاق الخيط.
 *
 * <p>كلُّ ما فيه مشروط بـ {@code center.tenancy.enabled}. على جهاز في سنتر لا يُنشأ
 * منه bean واحد، ولا يُسجَّل {@code MultiTenantConnectionProvider}، فيعمل Hibernate
 * كما كان بلا أن يمرّ باختبار "أيّ مؤسسة" عند كل جلسة.</p>
 *
 * <p>وقاعدة المنصة تُقرأ بـ {@link JdbcTemplate} لا بـ JPA، ومصدرُ بياناتها ليس
 * {@code @Primary}: مصنعُ الكيانات في هذا البرنامج موجَّهٌ إلى قاعدة المؤسسة الحالية،
 * ومصنعٌ ثانٍ بجواره يعني مديرَي معاملات ويجعل {@code @Transactional} في كل خدمة
 * سؤالاً عن أيّ قاعدة يقصد.</p>
 */
@Configuration
@EnableConfigurationProperties(TenancyProperties.class)
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled", havingValue = "true")
public class TenancyConfig {

    /** ترحيلات المنصة منفصلة عن ترحيلات المؤسسة: الاثنان يترقّيان بمعدّل مختلف */
    static final String PLATFORM_MIGRATIONS = "classpath:db/platform";

    /** ترحيلات قاعدة المؤسسة، تُشغَّل مرةً على كل قاعدة */
    public static final String TENANT_MIGRATIONS = "classpath:db/migration";

    @Bean
    public DataSource platformDataSource(TenancyProperties properties) {
        TenancyProperties.Platform platform = properties.getPlatform();
        if (platform.getUrl() == null || platform.getUrl().isBlank()) {
            throw new IllegalStateException(
                    "center.tenancy.platform.url is required when tenancy is enabled");
        }
        return DataSourceBuilder.create()
                .url(platform.getUrl())
                .username(platform.getUsername())
                .password(platform.getPassword())
                .build();
    }

    @Bean
    public JdbcTemplate platformJdbcTemplate(DataSource platformDataSource) {
        return new JdbcTemplate(platformDataSource);
    }

    /**
     * ترحيل قاعدة المنصة نفسها، قبل أن يُقرأ منها شيء.
     *
     * <p>{@code initMethod} لا {@code @PostConstruct} على السجلّ: الترتيب هنا شرط -
     * سجلٌّ يُقرأ قبل أن تُنشأ جداوله يُسقط الإقلاع برسالة عن جدول مفقود بدل رسالة
     * عن ترحيل لم يعمل.</p>
     */
    @Bean(initMethod = "migrate")
    public Flyway platformFlyway(DataSource platformDataSource) {
        return Flyway.configure()
                .dataSource(platformDataSource)
                .locations(PLATFORM_MIGRATIONS)
                .load();
    }

    @Bean
    public TenantRegistry tenantRegistry(JdbcTemplate platformJdbcTemplate, Flyway platformFlyway,
                                         Clock clock) {
        TenantRegistry registry = new TenantRegistry(platformJdbcTemplate, clock);
        registry.refresh();
        return registry;
    }

    /**
     * نطاق المؤسسة على الخيط، وهو نفسه ما يجيب عن {@link TenantContext} و{@link TenantSweep}.
     *
     * <p>{@code @Primary} لأن سطح المكتب يحمل {@code UserSession} وهي {@link TenantContext}
     * أيضاً؛ على خادمٍ لا وجود لها أصلاً، والأولوية هنا تجعل الحالتين تعملان بلا شرط
     * في الوسط.</p>
     */
    /**
     * جوابُ الخادم عن {@link com.codejava.center.core.tenant.TenantContext}
     * و{@link TenantSweep} معاً - bean واحد لا اثنان.
     *
     * <p>كان بجواره bean ثانٍ يعيد هذا الكائن نفسه باسم {@code TenantSweep}، وكلاهما
     * {@code @Primary}: أي مرشَّحان أوّليّان لنوعٍ واحد، وهو ما يرفضه Spring صراحةً
     * ({@code more than one 'primary' bean found}). والنتيجة أن الخادم متعدّد
     * المؤسسات لا يُقلع - ولم يظهر ذلك إلا حين أُقلع سياقٌ بتعدّد المؤسسات فعلاً،
     * لأن الاختبار الذي يفعل ذلك يحتاج Docker.</p>
     *
     * <p>ونوعُ الإرجاع {@code ServerTenantContext} لا أحد العقدين: Spring يطابق
     * بالنوع، فالصنف يلبّي العقدين معاً. وذكرُ عقدٍ واحد في التوقيع يُخفي الآخر عن
     * الحاقن.</p>
     */
    @Bean
    @Primary
    public ServerTenantContext serverTenantContext(TenantRegistry tenantRegistry) {
        return new ServerTenantContext(tenantRegistry);
    }

    /**
     * يسلّم Hibernate الموجِّهَ والمترجمَ.
     *
     * <p>ومعهما {@code ddl-auto=none}: فحصُ المخطط عند الإقلاع يسأل قاعدةً واحدة، ولا
     * توجد على خادمٍ قاعدةٌ واحدة يصحّ سؤالها - القواعد بعدد السناتر. البديل هو
     * {@link com.codejava.center.platform.TenantMigrations}: Flyway على كل قاعدة عند
     * الإقلاع، وهو أقوى لأنه يصلح لا يفحص فقط.</p>
     */
    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(
            SchemaPerTenantConnectionProvider connectionProvider,
            TenantSchemaResolver resolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
            properties.put(AvailableSettings.HBM2DDL_AUTO, "none");
        };
    }

    @Bean
    public SchemaPerTenantConnectionProvider schemaPerTenantConnectionProvider(DataSource dataSource) {
        return new SchemaPerTenantConnectionProvider(dataSource);
    }

    @Bean
    public TenantSchemaResolver tenantSchemaResolver(ServerTenantContext tenantContext,
                                                     TenantRegistry tenantRegistry) {
        return new TenantSchemaResolver(tenantContext, tenantRegistry);
    }

    /** لا شيء في الخدمات يعرف أيّ التنفيذين يعمل: كلاهما يجيب عن الواجهة نفسها */
    /**
     * ترقية مخطط كل مؤسسة عند الإقلاع.
     *
     * <p>{@code initMethod} لا حدثُ {@code ApplicationReadyEvent}: الترحيل يجب أن
     * يسبق أول استعلام لا أن يلحق آخر bean. وترتيبُه بعد السجلّ مضمون لأنه يأخذه
     * معاملاً.</p>
     */
    @Bean(initMethod = "migrateAll")
    public TenantMigrations tenantMigrations(DataSource dataSource, TenantRegistry tenantRegistry) {
        return new TenantMigrations(dataSource, tenantRegistry, TENANT_MIGRATIONS);
    }

    /**
     * مسحُ حالِ السنترات، لمشغّل المنصة.
     *
     * <p>هنا لا في {@code center-web}: قراءةُ خمسين قاعدةً كلٍّ في نطاقها عملٌ في طبقة
     * الأعمال، والحافةُ تنسخ الجواب في سجلٍّ صغير وتنتهي - كبقية ملفات {@code api/}.</p>
     */
    @Bean
    public PlatformOperations platformOperations(TenantRegistry tenantRegistry,
                                                 ServerTenantContext serverTenantContext,
                                                 SettingsService settingsService,
                                                 AlertRepository alertRepository,
                                                 Clock clock) {
        return new PlatformOperations(tenantRegistry, serverTenantContext,
                settingsService, alertRepository, clock);
    }

    @Bean
    public TenantProvisioning tenantProvisioning(DataSource dataSource,
                                                 JdbcTemplate platformJdbcTemplate,
                                                 TenantRegistry tenantRegistry,
                                                 TenantMigrations tenantMigrations,
                                                 ServerTenantContext serverTenantContext,
                                                 CenterSettingsRepository settingsRepository,
                                                 UserRepository userRepository,
                                                 InitialSetupService initialSetupService,
                                                 Clock clock) {
        return new TenantProvisioning(dataSource, platformJdbcTemplate, tenantRegistry,
                tenantMigrations, serverTenantContext, settingsRepository, userRepository,
                initialSetupService, clock);
    }

}
