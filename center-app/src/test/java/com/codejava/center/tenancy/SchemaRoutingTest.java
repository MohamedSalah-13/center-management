package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.SchemaPerTenantConnectionProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * توجيه الاتصال إلى قاعدة المؤسسة، وإعادتُه إلى حياده.
 *
 * <p>هذا هو كلّ الخيار (أ) من §3 في صنف واحد: لا استعلام في البرنامج يذكر المؤسسة،
 * فإن أخطأ هذا التوجيه قرأ سنترٌ بيانات سنترٍ آخر بلا أن يفشل شيء.</p>
 *
 * <p>على H2 لا على MySQL - وذلك مقصود لا تنازل. المسار المفحوص هنا هو
 * {@code setCatalog}/{@code setSchema}، وهو المسار نفسه الذي يعمل على MySQL؛ وما
 * يختلف بين القاعدتين ({@code CREATE DATABASE}، وترحيلات بلهجة MySQL) يفحصه
 * {@code MultiTenantIsolationIntegrationTest} على حاوية. ولو تُرك الأمر كلّه للحاوية
 * لَما فُحص هذا الصنف على جهاز مطوّر بلا Docker - أي في كل مرة تقريباً.</p>
 */
class SchemaRoutingTest {

    private JdbcDataSource dataSource;
    private SchemaPerTenantConnectionProvider provider;

    @BeforeEach
    void createTwoTenantSchemas() throws SQLException {
        dataSource = new JdbcDataSource();
        // قاعدة باسم جديد لكل اختبار: H2 في الذاكرة يحتفظ بما بُني بين الاختبارات
        dataSource.setURL("jdbc:h2:mem:routing_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // الأسماء مقتبسة لتبقى بحروف صغيرة: H2 يرفع حالة المعرّف غير المقتبس،
            // و{@code SchemaName} لا تُصدر إلا حروفاً صغيرة - وهو ما تحفظه MySQL كما يُكتب
            statement.execute("CREATE SCHEMA \"cairo\"");
            statement.execute("CREATE SCHEMA \"giza\"");
            statement.execute("CREATE TABLE \"cairo\".students (name VARCHAR(50))");
            statement.execute("CREATE TABLE \"giza\".students (name VARCHAR(50))");
            statement.execute("INSERT INTO \"cairo\".students VALUES ('طالب القاهرة')");
            statement.execute("INSERT INTO \"giza\".students VALUES ('طالب الجيزة')");
        }

        provider = new SchemaPerTenantConnectionProvider(dataSource);
    }

    /** الاستعلام نفسه، بلا ذكر للمؤسسة، يعيد بيانات مختلفة لكل مؤسسة */
    @Test
    void theSameQueryReadsEachTenantsOwnRows() throws SQLException {
        assertThat(readStudent("cairo")).isEqualTo("طالب القاهرة");
        assertThat(readStudent("giza")).isEqualTo("طالب الجيزة");
    }

    /**
     * الاتصال يعود إلى المجمّع لا يُغلق، فيجب أن يعود محايداً.
     *
     * <p>اتصالٌ سُلِّم وهو ما يزال يشير إلى قاعدة سنترٍ يُمنح بعد لحظة لعملٍ آخر. وهذا
     * الفرق لا يظهر إلا حين يُعاد استعمال الاتصال فعلاً - أي تحت الحمل، عند العميل، لا
     * في اختبارٍ ينشئ اتصالاً لكل استعلام. ولذلك يقف هذا الاختبار على <b>الاتصال
     * نفسه</b> بعد التسليم، بمصدرٍ يعيد الاتصال الواحد ويتجاهل إغلاقه - أي يفعل ما
     * يفعله المجمّع.</p>
     */
    @Test
    void aReleasedConnectionComesBackNeutral() throws SQLException {
        Connection physical = dataSource.getConnection();
        SchemaPerTenantConnectionProvider pooled =
                new SchemaPerTenantConnectionProvider(new OneConnection(physical));

        pooled.releaseAnyConnection(pooled.getAnyConnection());
        pooled.releaseConnection("cairo", pooled.getConnection("cairo"));

        assertThat(currentSchema(physical))
                .as("اتصالٌ عاد إلى المجمّع وهو داخل قاعدة سنتر يقرأها من بعده")
                .isEqualToIgnoringCase("PUBLIC");
        physical.close();
    }

    /** اتصالٌ لم تُذكر له مؤسسة يبقى على القاعدة الافتراضية ولا يُوجَّه إلى أحد */
    @Test
    void aConnectionWithNoTenantStaysOnTheDefault() throws SQLException {
        try (Connection connection = provider.getAnyConnection()) {
            assertThat(currentSchema(connection)).isEqualToIgnoringCase("PUBLIC");
        }
    }

    private String readStudent(String schema) throws SQLException {
        Connection connection = provider.getConnection(schema);
        try (Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT name FROM students")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        } finally {
            provider.releaseConnection(schema, connection);
        }
    }

    private String currentSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var rows = statement.executeQuery("CALL SCHEMA()")) {
            rows.next();
            return rows.getString(1);
        }
    }

    /** مصدرٌ يعيد الاتصال نفسه ولا يُغلقه: أبسط ما يحاكي مجمّع اتصالات */
    private record OneConnection(Connection connection) implements DataSource {

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> "close".equals(method.getName())
                            ? null
                            : method.invoke(connection, args));
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
