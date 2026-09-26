package com.codejava.center;

import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بوابة الإنتاج للمخطط: H2 يفحص الاستعلامات سريعاً، وهذا الاختبار وحده يثبت أن
 * ملفات Flyway بلهجة MySQL تنشئ قاعدة جديدة يوافق عليها Hibernate validate.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    /** أسماء ملفات الترحيل: {@code V<رقم>__<وصف>.sql}. */
    private static final Pattern MIGRATION_FILE = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("center_db")
            .withUsername("center_test")
            .withPassword("center_test_password")
            // بيانات الاختبار مؤقتة؛ الذاكرة تتجنب بطء قرص Docker على ويندوز.
            .withTmpFs(Map.of("/var/lib/mysql", "rw,size=768m"))
            // تهيئة mysql:8.0 الأولى تتجاوز الدقيقتين الافتراضيتين على جهاز بطيء.
            .withStartupTimeoutSeconds(300);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.show-sql", () -> "false");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    /**
     * المتوقع يُشتق من ملفات الترحيل نفسها، لا من رقم مكتوب هنا: رقم ثابت يتخلف عن
     * كل ترحيل جديد، والاختبار معطل بلا Docker فلا يلاحظ أحد.
     */
    @Test
    void appliesEveryMigrationAndCreatesConcurrentWriteGuards() throws IOException {
        List<Integer> migrationFiles = migrationVersionsOnClasspath();
        assertThat(migrationFiles).isNotEmpty();

        List<Integer> applied = jdbc.queryForList("""
                SELECT CAST(version AS UNSIGNED)
                FROM flyway_schema_history
                WHERE success = 1
                  AND version IS NOT NULL
                """, Integer.class);

        assertThat(applied).containsExactlyInAnyOrderElementsOf(migrationFiles);

        List<String> uniqueConstraints = jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'UNIQUE'
                  AND constraint_name IN (
                    'uk_attendance_student_session',
                    'uk_membership_student_group',
                    'uk_session_group_date',
                    'uk_session_active_group',
                    'uk_transaction_student_session_type',
                    'uk_tenant_slug',
                    'uk_branch_tenant_code')
                """, String.class);

        assertThat(uniqueConstraints).containsExactlyInAnyOrder(
                "uk_attendance_student_session",
                "uk_membership_student_group",
                "uk_session_group_date",
                "uk_session_active_group",
                "uk_transaction_student_session_type",
                "uk_tenant_slug",
                "uk_branch_tenant_code");
    }

    /** أساس المؤسسات والفروع (V16): جدولان، وقيد فريد لكل منهما، ومفتاح أجنبي بينهما. */
    @Test
    void createsTenantAndBranchFoundation() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('tenants', 'branches')
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder("tenants", "branches");

        List<String> foreignKeys = jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'branches'
                  AND referenced_table_name = 'tenants'
                """, String.class);
        assertThat(foreignKeys).containsExactly("fk_branch_tenant");
    }

    /** قرار "آخر مدير" يعتمد على هذا القفل، فيجب أن يعمل بلهجة MySQL لا H2 وحدها. */
    @Test
    @Transactional
    void locksUserRowsForAdministratorSafetyDecisions() {
        jdbc.update("insert into users (username, password, role) values (?, ?, ?)",
                "mysql-admin", "encoded", Role.ADMIN.name());

        assertThat(userRepository.findAllForUpdate())
                .extracting(user -> user.getUsername())
                .containsExactly("mysql-admin");
    }

    /** أرقام إصدارات كل ملف {@code db/migration/V*__*.sql} على مسار الأصناف. */
    private static List<Integer> migrationVersionsOnClasspath() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");
        return Arrays.stream(files)
                .map(Resource::getFilename)
                .map(MIGRATION_FILE::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .sorted()
                .toList();
    }
}
