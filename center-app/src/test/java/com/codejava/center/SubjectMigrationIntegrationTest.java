package com.codejava.center;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
@Testcontainers(disabledWithoutDocker = true)
class SubjectMigrationIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("subjects_upgrade").withUsername("subject_test").withPassword("subject_test_password")
            .withTmpFs(Map.of("/var/lib/mysql","rw,size=768m")).withStartupTimeoutSeconds(300);
    @Test void upgradeUnifiesAliasesAndPreservesTeachersAndCustomSubjects() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("16").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
             var insert = connection.prepareStatement("INSERT INTO teachers(name,subject,commission_type,commission_value) VALUES(?,?,'PERCENTAGE',50)")) {
            for (String subject : new String[]{"E","لغة انجليزية","انجليزى","اللغة الإنجليزية","عربى","رياضيات","روبوتات","  "}) {
                insert.setString(1, "معلم " + subject); insert.setString(2,subject); insert.executeUpdate();
            }
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            try (var statement=connection.createStatement()) {
                try (var rows=statement.executeQuery("SELECT COUNT(*) FROM teachers WHERE subject_id IS NOT NULL")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(8); }
                try (var rows=statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT subject_id) FROM teachers t JOIN subjects s ON s.id=t.subject_id WHERE s.name_key='english'")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(4); assertThat(rows.getInt(2)).isEqualTo(1);
                }
                try (var rows=statement.executeQuery("SELECT COUNT(*) FROM subjects WHERE name IN ('روبوتات','غير محدد')")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(2); }
                assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM subjects WHERE name_key='english'"))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
}
