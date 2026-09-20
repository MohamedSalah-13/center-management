package com.codejava.center.config.tenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * اتصالٌ موجَّه إلى قاعدة المؤسسة: خادمٌ واحد ومجمّعُ اتصالات واحد، وقاعدةٌ لكل سنتر.
 *
 * <p>هذا هو كلّ ما احتاجه الخيار (أ) من §3. لا استعلام في البرنامج يذكر المؤسسة، ولا
 * قيد فريد يتّسع لها، ولا عمود {@code tenant_id} في أربعين جدولاً: الاتصال نفسه يشير
 * إلى القاعدة الصحيحة قبل أن يُنفَّذ عليه شيء.</p>
 *
 * <p><b>وإعادةُ الاتصال إلى قاعدته الافتراضية عند التسليم هي نصف الصنف.</b> الاتصال
 * يعود إلى المجمّع لا يُغلق، فاتصالٌ سُلّم وهو ما يزال يشير إلى قاعدة سنترٍ يُمنح بعد
 * لحظة لعملٍ آخر - وإن كان ذلك العمل خارج نطاق مؤسسة قرأ بياناتِ من سبقه. الفشل هنا
 * لا يُسقط شيئاً ولا يظهر في سجل: بياناتٌ صحيحة تماماً تُعرض لمن لا يملكها.</p>
 *
 * <h2>لماذا تُستدعى دالّتان لتبديل القاعدة</h2>
 *
 * <p>MySQL لا تعرف إلا مستوىً واحداً من التسمية وتسمّيه catalog، وH2 تسمّي المستوى
 * نفسه schema. فـ {@code setCatalog} هي العاملة على الأولى و{@code setSchema} على
 * الثانية، والأخرى لا تفعل شيئاً في كلتا الحالتين. استدعاؤهما معاً ليس ترجيحاً بين
 * احتمالين بل تسميةُ الشيء نفسه بالاسمين اللذين يعرفهما السائقان - وهو ما يجعل
 * الاختبار على H2 يفحص المسار الذي يعمل على MySQL لا مساراً يشبهه.</p>
 */
public class SchemaPerTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(SchemaPerTenantConnectionProvider.class);

    private final DataSource dataSource;

    /**
     * ما يعود إليه الاتصال عند التسليم، بالاسمين معاً.
     *
     * <p>يُقرأ من الاتصال نفسه أول مرة لا يُكتب في إعداد: مصدرُ البيانات يعرف قاعدته،
     * وقيمةٌ مكتوبة بجواره تختلف عنها يوماً بلا أن يقول شيء.</p>
     *
     * <p>والاثنان محفوظان منفصلين لأنهما قد لا يتطابقان: على MySQL الـ catalog هو
     * القاعدة والـ schema فارغ، وعلى H2 الـ catalog اسمُ الملف والـ schema هو
     * {@code PUBLIC}. حفظُ واحدٍ منهما وإعادةُ الآخر إليه يعني إعادةً تفشل - ثم اتصالاً
     * يُغلق بدل أن يُعاد، فيُخفي عطلَ التوجيه خلف كلفةٍ في الأداء لا أحد يربطها به.</p>
     */
    private volatile String defaultCatalog;

    private volatile String defaultSchema;

    private volatile boolean defaultsKnown;

    public SchemaPerTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * اتصالٌ بلا مؤسسة: تستعمله أدوات Hibernate الداخلية (قراءة بيانات المخطط عند
     * الإقلاع مثلاً). يُترك على القاعدة الافتراضية ولا يُوجَّه إلى أحد.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        rememberDefault(connection);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String database) throws SQLException {
        Connection connection = dataSource.getConnection();
        rememberDefault(connection);
        route(connection, database);
        return connection;
    }

    @Override
    public void releaseConnection(String database, Connection connection) throws SQLException {
        try {
            restore(connection);
        } catch (SQLException e) {
            // اتصالٌ لا يمكن إعادته إلى حياده لا يعود إلى المجمّع: إغلاقه يكلّف اتصالاً
            // جديداً، وإبقاؤه يكلّف تسرّباً
            log.warn("تعذّرت إعادة الاتصال إلى القاعدة الافتراضية؛ يُغلق بدل أن يُعاد: {}",
                    e.getMessage());
        } finally {
            connection.close();
        }
    }

    /**
     * {@code false}: Hibernate يطلق الاتصال بعد كل عبارة ويعود ليطلبه، وهو ما يعني
     * توجيهاً جديداً عند كل عبارة داخل المعاملة الواحدة - كلفةٌ بلا مقابل هنا.
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> type) {
        return type.isAssignableFrom(getClass()) || type.isAssignableFrom(DataSource.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(getClass())) {
            return (T) this;
        }
        if (type.isAssignableFrom(DataSource.class)) {
            return (T) dataSource;
        }
        throw new UnsupportedOperationException("cannot unwrap to " + type);
    }

    private void route(Connection connection, String database) throws SQLException {
        connection.setCatalog(database);
        connection.setSchema(database);
    }

    /** يُعاد كلٌّ إلى ما كان، وما كان فارغاً يُترك: القاعدة لا تعرف اسماً فارغاً */
    private void restore(Connection connection) throws SQLException {
        if (defaultCatalog != null) {
            connection.setCatalog(defaultCatalog);
        }
        if (defaultSchema != null) {
            connection.setSchema(defaultSchema);
        }
    }

    private void rememberDefault(Connection connection) throws SQLException {
        if (!defaultsKnown) {
            defaultCatalog = connection.getCatalog();
            defaultSchema = connection.getSchema();
            defaultsKnown = true;
        }
    }
}
