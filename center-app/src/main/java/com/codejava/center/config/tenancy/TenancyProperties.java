package com.codejava.center.config.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * إعداد تعدّد المؤسسات، و<b>الافتراضي فيه معطّل</b>.
 *
 * <p>ذلك ليس حذراً بل وصفٌ للواقع: سطح المكتب يخدم سنتراً واحداً في قاعدة واحدة،
 * ولا قاعدة منصة على جهازٍ في سنتر. وما دام معطّلاً فلا {@code MultiTenantConnectionProvider}
 * يُسجَّل، فـ Hibernate لا يعرف أصلاً أن للمؤسسات تعدّداً - أي أن مسار سطح المكتب هو
 * المسار نفسه الذي كان قبل هذه المرحلة، لا مسارٌ يمرّ بشرطٍ يقول "مؤسسة واحدة".</p>
 *
 * <p>وبيانات قاعدة المنصة مطلوبة عند التفعيل ولا افتراض لها - نفس قاعدة
 * {@code DB_PASSWORD}: خادمٌ يُقلع بقاعدة منصة خاطئة أسوأ من خادمٍ يرفض الإقلاع.</p>
 */
@ConfigurationProperties(prefix = "center.tenancy")
public class TenancyProperties {

    private boolean enabled;

    private final Platform platform = new Platform();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Platform getPlatform() {
        return platform;
    }

    /** قاعدة سجلّ المنصة: من هي المؤسسات وأين قواعدها. */
    public static class Platform {

        private String url;
        private String username;
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
