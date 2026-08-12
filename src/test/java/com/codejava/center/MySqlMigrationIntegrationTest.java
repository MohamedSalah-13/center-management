package com.codejava.center;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * بوابة الإنتاج للمخطط: H2 يفحص الاستعلامات سريعاً، وهذا الاختبار وحده يثبت أن
 * ملفات Flyway بلهجة MySQL تنشئ قاعدة جديدة يوافق عليها Hibernate validate.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("center_db")
            .withUsername("center_test")
            .withPassword("center_test_password");

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

    @Test
    void appliesEveryMigrationAndCreatesConcurrentWriteGuards() {
        String latestVersion = jdbc.queryForObject("""
                SELECT MAX(CAST(version AS UNSIGNED))
                FROM flyway_schema_history
                WHERE success = 1
                """, String.class);
        assertThat(latestVersion).isEqualTo("15");

        List<String> constraints = jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'UNIQUE'
                  AND constraint_name IN (
                    'uk_attendance_student_session',
                    'uk_membership_student_group',
                    'uk_session_group_date',
                    'uk_session_active_group',
                    'uk_transaction_student_session_type')
                """, String.class);

        assertThat(constraints).containsExactlyInAnyOrder(
                "uk_attendance_student_session",
                "uk_membership_student_group",
                "uk_session_group_date",
                "uk_session_active_group",
                "uk_transaction_student_session_type");
    }
}
