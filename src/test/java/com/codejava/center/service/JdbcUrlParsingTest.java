package com.codejava.center.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * قراءة المضيف والمنفذ واسم القاعدة من رابط JDBC.
 *
 * <p>كان {@code BackupService} يستدعي {@code mysqldump} بلا {@code --host} ولا
 * {@code --port}، فتذهب الأداة إلى {@code localhost:3306} مهما كان الرابط. السنتر الذي
 * يشغّل MySQL في Docker على منفذ آخر - وهي الحالة التي يحذّر منها
 * {@code docs/first-install.md} - كان يظن أن لديه نسخاً احتياطية وهي إما فاشلة أو
 * مأخوذة من قاعدة أخرى تماماً.</p>
 */
class JdbcUrlParsingTest {

    private final BackupService service = new BackupService();

    @Test
    void readsHostPortAndDatabaseFromTheDefaultUrl() {
        String url = "jdbc:mysql://localhost:3306/center_db?useSSL=false&serverTimezone=UTC";

        assertThat(service.host(url)).isEqualTo("localhost");
        assertThat(service.port(url)).isEqualTo("3306");
        assertThat(service.dbName(url)).isEqualTo("center_db");
    }

    @Test
    void readsAContainerOrRemoteServerOnAnotherPort() {
        String url = "jdbc:mysql://192.168.1.20:3307/center_db";

        assertThat(service.host(url)).isEqualTo("192.168.1.20");
        assertThat(service.port(url)).isEqualTo("3307");
        assertThat(service.dbName(url)).isEqualTo("center_db");
    }

    @Test
    void fallsBackToTheDefaultPortWhenTheUrlOmitsIt() {
        String url = "jdbc:mysql://db.example.com/center_db";

        assertThat(service.host(url)).isEqualTo("db.example.com");
        assertThat(service.port(url)).isEqualTo("3306");
    }

    @Test
    void aUrlWithoutADatabaseNameFailsLoudly() {
        assertThatThrownBy(() -> service.dbName("jdbc:mysql://localhost:3306/"))
                .isInstanceOf(IllegalStateException.class);
    }
}
