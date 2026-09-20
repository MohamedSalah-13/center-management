package com.codejava.center.config;

import com.codejava.center.core.backup.BackupTarget;
import com.codejava.center.util.JdbcUrl;
import com.codejava.center.util.MySqlLocator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * تركيب {@link BackupTarget} على هذا الطرف: القاعدة التي يعمل عليها البرنامج نفسه،
 * والأدوات كما هي مثبَّتة على هذا الجهاز.
 *
 * <p>الاعتماد يأتي من متغيرات البيئة عبر {@code spring.datasource.*} لا من نصّ في الكود —
 * ولا كلمة مرور افتراضية، فيفشل التشغيل بصوت عالٍ بدل أن يعمل بسرّ مكتوب في المستودع.</p>
 */
@Component
public class DesktopBackupTarget implements BackupTarget {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    /**
     * مجلد أدوات MySQL مضبوطاً صراحةً. تصريحٌ فارغ لا يعني عدم البحث: عندها وحدها
     * يُستشار {@link MySqlLocator}، الذي يعيد {@code null} لو كانت الأدوات في
     * {@code PATH} أصلاً أو تعذّر إيجادها بفحص مجلدات التركيب المعروفة.
     */
    @Value("${center.backup.mysql-bin-dir:}")
    private String mysqlBinDir;

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
        return JdbcUrl.database(jdbcUrl);
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
        if (mysqlBinDir != null && !mysqlBinDir.isBlank()) {
            return mysqlBinDir;
        }
        return MySqlLocator.resolve();
    }
}
