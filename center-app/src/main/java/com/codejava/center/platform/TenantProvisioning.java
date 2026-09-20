package com.codejava.center.platform;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.User;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.service.InitialSetupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * فتحُ سنترٍ جديد على المنصة: قاعدةٌ له، ومخططٌ فيها، وصفُّ إعدادات، ورمزُ دعوة لمديره.
 *
 * <p>خمس خطوات في قاعدتين، ولا معاملة تجمعهما - لأن {@code CREATE DATABASE} في MySQL
 * يُثبِّت ما قبله ولا يتراجع. فالترتيب هو ما يحمي: تُبنى قاعدة المؤسسة كاملةً أولاً،
 * ولا يُكتب صفُّها في سجلّ المنصة إلا بعد أن تصير صالحة. الانقطاع في المنتصف يترك
 * قاعدةً لا يعرفها أحد - وهي نفايةٌ تُنظَّف، لا مؤسسةً مسجّلة بقاعدة نصف جاهزة تُخدَم
 * غداً وتفشل عند أول شاشة.</p>
 *
 * <p>وعبارةُ {@code CREATE DATABASE} هي الموضع الوحيد الذي يدخل فيه نصٌّ من الخارج
 * إلى أمر DDL؛ {@link SchemaName} هي الحارس، وبينه وبين هذه العبارة لا يمرّ اسم لم
 * يمرّ عليه. ولهذا لا تُنشأ القاعدة من اسم المؤسسة مباشرةً بل من اسمٍ مُتحقَّق منه.</p>
 *
 * <p><b>لهجة MySQL صريحة هنا</b>، كما في ملفات الترحيل نفسها: {@code CREATE DATABASE}
 * ليست عبارةً معيارية، وH2 لا تعرفها. فما يفحص هذا الصنف هو
 * {@code MultiTenantIsolationIntegrationTest} على حاوية MySQL، لا اختبارٌ على H2 يشبهه.</p>
 */
public class TenantProvisioning {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioning.class);

    /** مهلة رمز الدعوة: أسبوع. رمزٌ لا ينتهي هو كلمة سر دائمة في بريد أحدهم */
    public static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    private final DataSource tenantDataSource;
    private final JdbcTemplate platform;
    private final TenantRegistry registry;
    private final TenantMigrations migrations;
    private final ServerTenantContext tenantContext;
    private final CenterSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final InitialSetupService initialSetup;
    private final Clock clock;

    public TenantProvisioning(DataSource tenantDataSource, JdbcTemplate platform,
                              TenantRegistry registry, TenantMigrations migrations,
                              ServerTenantContext tenantContext,
                              CenterSettingsRepository settingsRepository,
                              UserRepository userRepository,
                              InitialSetupService initialSetup, Clock clock) {
        this.tenantDataSource = tenantDataSource;
        this.platform = platform;
        this.registry = registry;
        this.migrations = migrations;
        this.tenantContext = tenantContext;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.initialSetup = initialSetup;
        this.clock = clock;
    }

    /**
     * يفتح مؤسسة جديدة ويعيد رمز دعوة مديرها الأول.
     *
     * <p>الرمز يُعاد هنا ولا يُحفظ في أي مكان بصيغته الأصلية - في سجلّ المنصة بصمتُه
     * وحدها. من يستدعي هذه الدالة هو من يسلّمه، ومن ضاع منه رمزُه يأخذ رمزاً جديداً
     * لا رمزه القديم.</p>
     */
    public NewTenant open(String name, String slug, SchemaName schema) {
        createDatabase(schema);

        TenantId id = insertTenantRow(name, slug, schema);
        PlatformTenant tenant = new PlatformTenant(id, name, slug, schema, TenantStatus.ACTIVE);

        migrations.migrate(tenant);
        seedSettings(tenant, name);
        registry.refresh();

        String code = issueInvite(id);
        log.info("فُتحت المؤسسة {} ({}) في القاعدة {}", slug, id.value(), schema);
        return new NewTenant(tenant, code);
    }

    /**
     * ينشئ حساب المدير الأول مقابل رمز دعوة صالح.
     *
     * <p><b>الرمز يُستهلك بعد إنشاء الحساب لا قبله</b>، والترتيب مقصود: القاعدتان
     * منفصلتان فلا معاملة تجمعهما، وفشلٌ بين الخطوتين يجب أن يترك الحال القابل
     * للإصلاح. "أُنشئ الحساب ولم يُستهلك الرمز" يُصلحه {@code count() != 0} داخل
     * {@link InitialSetupService} - الرمز يبقى ظاهراً صالحاً ولا يفتح شيئاً. والعكس -
     * "استُهلك الرمز ولا حساب" - يترك سنتراً لا سبيل إلى دخوله إلا بتدخّل يدوي.</p>
     */
    public User redeemInvite(String code, String password, String confirmation) {
        String fingerprint = InviteCode.fingerprint(code);
        TenantId tenant = findRedeemableTenant(fingerprint);

        User admin = tenantContext.call(tenant, () -> initialSetup.createInitialAdmin(password, confirmation));

        platform.update("UPDATE tenant_invites SET redeemed_at = ? WHERE code_hash = ?",
                LocalDateTime.now(clock), fingerprint);
        log.info("فُعِّلت دعوة المؤسسة {}", tenant.value());
        return admin;
    }

    /** رمز دعوة جديد لمؤسسة قائمة: لمن ضاع رمزه أو انتهت مهلته */
    public String issueInvite(TenantId tenant) {
        String code = InviteCode.generate();
        platform.update("""
                INSERT INTO tenant_invites (tenant_id, code_hash, expires_at)
                VALUES (?, ?, ?)
                """, tenant.value(), InviteCode.fingerprint(code),
                LocalDateTime.now(clock).plus(INVITE_VALIDITY));
        return code;
    }

    /** تغيير حال الاشتراك: يسري على الدورة التالية للمجدولين فوراً بعد إعادة القراءة */
    public void changeStatus(TenantId tenant, TenantStatus status) {
        platform.update("UPDATE tenants SET status = ? WHERE id = ?", status.name(), tenant.value());
        registry.refresh();
    }

    private TenantId findRedeemableTenant(String fingerprint) {
        // نفس الرسالة للرمز المجهول وللمستهلك وللمنتهي: التفريق بينها يقول لمن يجرّب
        // الرموز أيُّ تخمين كان قريباً
        var rows = platform.queryForList("""
                SELECT tenant_id
                FROM tenant_invites
                WHERE code_hash = ?
                  AND redeemed_at IS NULL
                  AND expires_at > ?
                """, Long.class, fingerprint, LocalDateTime.now(clock));

        if (rows.size() != 1) {
            throw new IllegalStateException("invite code is not valid");
        }
        return new TenantId(rows.get(0));
    }

    /**
     * {@code CREATE DATABASE} وليس {@code IF NOT EXISTS}: قاعدةٌ موجودة باسم مؤسسة
     * جديدة تعني إمّا تزويداً انقطع وترك نفايةً، وإمّا سنتراً قائماً على وشك أن
     * تُكتب ترحيلاتٌ فوق بياناته. الاثنان يستحقان وقوفاً لا متابعةً صامتة.
     */
    private void createDatabase(SchemaName schema) {
        new JdbcTemplate(tenantDataSource).execute(
                "CREATE DATABASE `" + schema.value() + "` "
                        + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private TenantId insertTenantRow(String name, String slug, SchemaName schema) {
        try {
            platform.update("""
                    INSERT INTO tenants (name, slug, schema_name, status)
                    VALUES (?, ?, ?, ?)
                    """, name, slug, schema.value(), TenantStatus.ACTIVE.name());
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("a tenant with this slug or schema already exists", e);
        }
        Long id = platform.queryForObject("SELECT id FROM tenants WHERE slug = ?", Long.class, slug);
        return new TenantId(id);
    }

    /**
     * صفُّ الإعدادات الأول.
     *
     * <p>المخطط لا يزرعه - وهو الصواب على جهازٍ يُركَّب فيملأه صاحبه - لكن مؤسسةً
     * تُفتح عن بُعد لا أحد أمامها ليكتب اسمها، واسمُ السنتر يظهر على كل إيصال وكل
     * كشف. ويُكتب بالمستودع مباشرةً لا بـ {@code SettingsService}: تلك تنشر
     * {@code SettingsChangedEvent} فتعيد جدولة نسخٍ وتنبيهاتٍ لمؤسسة لم يدخلها أحد بعد.</p>
     */
    private void seedSettings(PlatformTenant tenant, String name) {
        tenantContext.within(tenant.id(), () ->
                settingsRepository.save(CenterSettings.builder()
                        .id(1L)
                        .centerName(name)
                        .build()));
    }

    /** المؤسسة كما صارت، ورمزُ دعوة مديرها الأول - يُقرأ مرة ولا يُحفظ */
    public record NewTenant(PlatformTenant tenant, String inviteCode) {
    }

    /** أفيها مستخدمون بعد؟ لشاشة إدارة المنصة: مؤسسةٌ فُتحت ولم يدخلها أحد */
    public boolean hasUsers(TenantId tenant) {
        return tenantContext.call(tenant, () -> userRepository.count() > 0);
    }
}
