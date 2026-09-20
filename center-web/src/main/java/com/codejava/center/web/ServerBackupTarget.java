package com.codejava.center.web;

import com.codejava.center.core.backup.BackupTarget;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.util.JdbcUrl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;

/**
 * ما الذي تنسخه نسخةُ هذا الخادم، وأين تُكتب.
 *
 * <p>الفرق عن الجهاز سطرٌ واحد وهو بيت القصيد: {@code DesktopBackupTarget} يجيب
 * باسم القاعدة المكتوب في رابط الاتصال، لأن البرنامج هناك يخدم قاعدةً واحدة هي
 * قاعدة السنتر. وخادمٌ يخدم مئة سنتر يتصل بخادم قواعد واحد، فالجواب نفسه هناك
 * يعني نسخةً واحدة تحمل بيانات المئة - تُسلَّم لمن يطلب نسخته.</p>
 *
 * <p>فالقاعدة هنا قاعدةُ <b>مؤسسة الدورة الجارية</b>، والمجدوِل يدخل نطاق كل مؤسسة
 * قبل أن يستدعي النسخ، فالسؤال يُطرح وهو داخلها.</p>
 *
 * <p>و{@link #backupRoot()} يجيب بمجلّد تلك المؤسسة وحدها، لا بـ {@code null} كما
 * يجيب الجهاز: هناك القرص قرصُ صاحبه، وتقييده يمنع الفلاشة وقرص الشبكة - وهما
 * الموضعان اللذان تُكتب فيهما النسخ فعلاً. وهنا نفسُ النصّ الحرّ مسارُ كتابةٍ على
 * قرص مضيف تشترك فيه مئة مؤسسة، فيقيّده {@code ContainedPath} بما تحته.</p>
 */
public class ServerBackupTarget implements BackupTarget {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${center.backup.mysql-bin-dir:}")
    private String mysqlBinDir;

    /** جذر النسخ على هذا الخادم؛ فارغٌ يعني "لا جذر" كما على الجهاز */
    @Value("${center.backup.root:}")
    private String backupRoot;

    private final CurrentCentre centre;

    /** سجلّ المنصة إن وُجد؛ غائبٌ على خادمٍ يخدم سنتراً واحداً */
    private final ObjectProvider<TenantRegistry> registry;

    public ServerBackupTarget(CurrentCentre centre, ObjectProvider<TenantRegistry> registry) {
        this.centre = centre;
        this.registry = registry;
    }

    @Override
    public String host() {
        return JdbcUrl.host(jdbcUrl);
    }

    @Override
    public String port() {
        return JdbcUrl.port(jdbcUrl);
    }

    @Override
    public String database() {
        return schema().orElse(JdbcUrl.database(jdbcUrl));
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String toolDirectory() {
        // لا بحث في مسارات ويندوز كما يفعل الجهاز: صورةُ الخادم يبنيها من ينشرها،
        // ومسارُ الأدوات فيها إعدادٌ يُكتب مرة لا شيء يُخمَّن عند كل نسخة
        return mysqlBinDir == null || mysqlBinDir.isBlank() ? "" : mysqlBinDir;
    }

    @Override
    public Path backupRoot() {
        if (backupRoot == null || backupRoot.isBlank()) {
            return null;
        }
        Path root = Path.of(backupRoot);
        return schema().map(root::resolve).orElse(root);
    }

    /**
     * اسمُ قاعدة المؤسسة الجارية، أو لا شيء على تركيبٍ بسنترٍ واحد.
     *
     * <p>اسمُ المخطَّط لا الاسم المقروء: {@code SchemaName} تحقّق منه حرفاً حرفاً قبل
     * أن يدخل {@code CREATE DATABASE}، وهو ما يجعله صالحاً لاسم مجلَّد كذلك.</p>
     */
    private java.util.Optional<String> schema() {
        TenantId tenant = centre.boundOrNull();
        TenantRegistry platform = registry.getIfAvailable();
        if (tenant == null || platform == null) {
            return java.util.Optional.empty();
        }
        return platform.find(tenant).map(PlatformTenant::schema).map(schema -> schema.value());
    }
}
