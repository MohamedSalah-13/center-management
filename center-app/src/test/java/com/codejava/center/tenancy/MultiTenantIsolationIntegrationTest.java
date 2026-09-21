package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.User;
import com.codejava.center.platform.CentreOperations;
import com.codejava.center.platform.PlatformOperations;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantProvisioning;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.platform.TenantStatus;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * مؤسستان على خادم واحد: ما يقع في إحداهما لا يظهر في الأخرى.
 *
 * <p>هذا هو معيار انتهاء المرحلة 2، ولا يمكن فحصه إلا على MySQL حقيقية: الخيار (أ) من
 * §3 يقوم على {@code CREATE DATABASE} وعلى توجيه الاتصال وعلى تشغيل Flyway على كل
 * قاعدة - وثلاثتها لهجةُ خادمٍ لا يحاكيها H2. أما <b>قرار التوجيه نفسه</b> فيفحصه
 * {@link SchemaRoutingTest} بلا حاوية، حتى لا يبقى أهمّ ما في المرحلة بلا فحص على
 * جهاز مطوّر بلا Docker.</p>
 *
 * <p>ويعمل الاتصال هنا بـ {@code root}: التزويد ينشئ قواعد بيانات، وهي صلاحية على
 * مستوى الخادم لا يملكها مستخدم السنتر المحدود الذي يصفه
 * {@code docs/first-install.md}. وهو فرقٌ حقيقي بين النشرتين لا التفافٌ على اختبار:
 * من يفتح سناتر جديدة على منصة ليس هو من يشغّل سنتراً واحداً على جهازه.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MultiTenantIsolationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("center_db")
            .withUsername("center_test")
            .withPassword("center_test_password")
            .withStartupTimeoutSeconds(300);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.show-sql", () -> "false");

        // ترحيلات المؤسسة تُشغَّل على كل قاعدة على حدة لا على قاعدة الاتصال
        properties.add("spring.flyway.enabled", () -> "false");

        properties.add("center.tenancy.enabled", () -> "true");
        // الرابط يُبنى ولا يُشتقّ بالاستبدال من رابط الحاوية: ذاك قد يحمل معاملات
        // فيصير في الناتج علامتا استفهام. وcreateDatabaseIfNotExist لأن قاعدة المنصة
        // لا تنشئها الحاوية وهي أول ما يُقرأ
        properties.add("center.tenancy.platform.url", () -> "jdbc:mysql://"
                + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT)
                + "/center_platform?createDatabaseIfNotExist=true");
        properties.add("center.tenancy.platform.username", () -> "root");
        properties.add("center.tenancy.platform.password", MYSQL::getPassword);
    }

    @Autowired private TenantProvisioning provisioning;
    @Autowired private TenantRegistry registry;
    @Autowired private ServerTenantContext tenants;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SettingsService settingsService;
    @Autowired private JdbcTemplate platformJdbcTemplate;
    @Autowired private PlatformOperations operations;

    private PlatformTenant cairo;
    private PlatformTenant giza;
    private String cairoInvite;

    @BeforeEach
    void openTwoCentres() {
        // أسماء فريدة لكل تشغيل: CREATE DATABASE لا تقبل قاعدةً قائمة، عن قصد
        String suffix = Long.toString(System.nanoTime(), 36).toLowerCase();
        TenantProvisioning.NewTenant first =
                provisioning.open("سنتر القاهرة", "cairo_" + suffix, SchemaName.of("t_cairo_" + suffix));
        TenantProvisioning.NewTenant second =
                provisioning.open("سنتر الجيزة", "giza_" + suffix, SchemaName.of("t_giza_" + suffix));

        cairo = first.tenant();
        giza = second.tenant();
        cairoInvite = first.inviteCode();
    }

    /** التزويد: قاعدةٌ مبنية بالمخطط كاملاً، وصفُّ إعدادات باسم السنتر */
    @Test
    void provisioningBuildsAFullSchemaAndNamesTheCentre() {
        assertThat(registry.find(cairo.id())).isPresent();

        Integer tables = platformJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?
                """, Integer.class, cairo.schema().value());
        assertThat(tables).isGreaterThan(15);

        assertThat(tenants.call(cairo.id(), () -> settingsService.getSettings().getCenterName()))
                .isEqualTo("سنتر القاهرة");
        assertThat(tenants.call(giza.id(), () -> settingsService.getSettings().getCenterName()))
                .isEqualTo("سنتر الجيزة");
    }

    /**
     * معيار الانتهاء: الاستعلام نفسه - بلا ذكرٍ للمؤسسة في أي مستودع - يرى بيانات
     * مؤسسته وحدها.
     */
    @Test
    void aStudentRegisteredInOneCentreDoesNotExistInTheOther() {
        tenants.within(cairo.id(), () -> studentRepository.save(Student.builder()
                .name("طالب القاهرة")
                .barcode("C-1")
                .build()));

        List<Student> inCairo = tenants.call(cairo.id(), () -> studentRepository.findAll());
        List<Student> inGiza = tenants.call(giza.id(), () -> studentRepository.findAll());

        assertThat(inCairo).extracting(Student::getName).containsExactly("طالب القاهرة");
        assertThat(inGiza).isEmpty();
    }

    /** رمز الدعوة يفتح حساب المدير في مؤسسته وحدها، ولا يُقبل مرتين */
    @Test
    void anInviteCreatesTheAdminOfItsOwnCentreAndIsSpent() {
        User admin = provisioning.redeemInvite(cairoInvite, "Str0ng-Pass!", "Str0ng-Pass!");

        assertThat(admin.getUsername()).isNotBlank();
        assertThat(tenants.call(cairo.id(), () -> userRepository.count())).isEqualTo(1);
        assertThat(tenants.call(giza.id(), () -> userRepository.count()))
                .as("سنترٌ لم تُفعَّل دعوته لا يصير له مدير")
                .isZero();

        assertThatThrownBy(() -> provisioning.redeemInvite(cairoInvite, "Other-Pass1!", "Other-Pass1!"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * مسحُ المشغّل يقرأ كل سنترٍ من قاعدته هو.
     *
     * <p>وهو الموضع الوحيد الذي يمكن أن يُختبر فيه فعلاً: {@code PlatformOperationsTest}
     * يغطّي الحلقة ببديلٍ عن القارئ، وما يبقى - أن القراءة داخل النطاق تصيب مخطَّط
     * صاحبها لا مخطَّط جاره - لا يظهر إلا بقاعدتين حقيقيتين. ولو أصابت المخطَّط الخطأ
     * لَعاد المسحُ صفوفاً صحيحة الشكل تصف السنتر الخطأ، ولا شيء يفشل.</p>
     */
    @Test
    void theOperatorSurveyReadsEachCentreFromItsOwnSchema() {
        LocalDateTime lastBackup = LocalDateTime.of(2026, 9, 20, 2, 0);
        tenants.within(cairo.id(), () -> settingsService.recordAutoBackupAt(lastBackup));

        List<CentreOperations> survey = operations.survey();

        CentreOperations cairoRow = row(survey, cairo.slug());
        CentreOperations gizaRow = row(survey, giza.slug());

        assertThat(cairoRow.readable()).isTrue();
        assertThat(gizaRow.readable()).isTrue();

        assertThat(cairoRow.name()).isEqualTo("سنتر القاهرة");
        assertThat(cairoRow.lastBackupAt()).isEqualTo(lastBackup);
        assertThat(gizaRow.lastBackupAt())
                .as("ختمُ سنترٍ لا يُقرأ من قاعدة جاره")
                .isNull();
    }

    private static CentreOperations row(List<CentreOperations> survey, String slug) {
        return survey.stream().filter(centre -> slug.equals(centre.slug())).findFirst()
                .orElseThrow(() -> new AssertionError("لا سطر في المسح للسنتر " + slug));
    }

    @Test
    void anUnknownInviteIsRefused() {
        assertThatThrownBy(() -> provisioning.redeemInvite("AAAAA-BBBBB-CCCCC", "Str0ng-Pass!", "Str0ng-Pass!"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * الدورة الليلية تمرّ على المخدومة وحدها: سنترٌ توقّف اشتراكه لا تُؤخذ نسخته ولا
     * تُرسل رسائل باسمه.
     */
    @Test
    void theSweepSkipsASuspendedCentre() {
        provisioning.changeStatus(giza.id(), TenantStatus.SUSPENDED);

        List<TenantId> visited = new java.util.ArrayList<>();
        tenants.sweep(visited::add);

        assertThat(visited).contains(cairo.id()).doesNotContain(giza.id());
    }

    /** خيطٌ بلا مؤسسة لا يقرأ من قاعدةٍ افتراضية: عطلٌ صاخب أهون من تسرّب صامت */
    @Test
    void aQueryOutsideAnyTenantFailsInsteadOfReadingSomebody() {
        // بالأثر لا بنوع الاستثناء: Spring وHibernate يلفّان ما يُرمى داخل فتح الجلسة
        // بأغلفة تختلف بين الإصدارات، والرسالة هي ما يُقصد
        assertThatThrownBy(() -> studentRepository.count())
                .hasStackTraceContaining("no tenant on this thread");
    }
}
