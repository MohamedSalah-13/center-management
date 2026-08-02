package com.codejava.center;

import com.codejava.center.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * يولّد مخطط MySQL من الكيانات مباشرةً إلى target/schema-mysql.sql.
 *
 * <p>ليس اختباراً للسلوك بل أداة توليد: ملف Flyway الأول يجب أن يطابق ما يتوقعه
 * Hibernate بالضبط، لأن {@code ddl-auto=validate} يرفض بدء التشغيل عند أي اختلاف.
 * كتابة المخطط يدوياً تعني اكتشاف الفروق عند أول تشغيل عند العميل لا هنا.</p>
 *
 * <p>يستخدم توليد المخطط القياسي في JPA مع لهجة MySQL بينما الاتصال على H2:
 * لا يُنفَّذ أي DDL على قاعدة البيانات (database.action=none)، بل يُكتب السكربت فقط.</p>
 *
 * <p><b>عند تغيير أي كيان:</b> شغّل هذا الصنف، قارن الناتج بالمخطط الحالي، وأضف ملف
 * ترحيل V جديداً بالفروق. لا تعدّل ملف ترحيل طُبِّق سلفاً.</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/schema-mysql.sql"
})
@ContextConfiguration(classes = SchemaScriptGenerator.GeneratorConfig.class)
class SchemaScriptGenerator {

    @Test
    void writesMySqlSchemaScript() {
        assertThat(new File("target/schema-mysql.sql"))
                .exists()
                .content().contains("create table");
    }

    /**
     * سياق يقتصر على الكيانات.
     * لا يستخدم CenterApplication لأنه CommandLineRunner ينشئ حساب المدير عند الإقلاع،
     * وهو يفشل هنا إذ لا تُنشأ الجداول أصلاً (ddl-auto=none).
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.codejava.center.domain")
    static class GeneratorConfig {
    }
}
