package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.ServerTenantContext;
import com.codejava.center.config.tenancy.TenantSchemaResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * "أيّ مؤسسة" حين لا مؤسسة.
 *
 * <p>كان هذا الصنف يُسقط العمل هنا، وهو صحيحٌ في المعنى وخطأٌ في الموضع: <b>يمنع
 * الخادم من الإقلاع أصلاً</b>. Spring Data يفتح جلسةً واحدة عند بناء كل مستودع
 * ليعرف أيّ مزوّد JPA خلفه، وHibernate يسأل عن المؤسسة كلما فُتحت جلسة - وخيطُ
 * الإقلاع لا مؤسسة له بطبيعته، ولا يُفترض أن تكون له.</p>
 *
 * <p>ولم يظهر العطل محلياً لأن الاختبار الذي يُقلع بتعدّد المؤسسات يحتاج Docker
 * ويُتخطّى بدونه - وهو بالضبط ما يوجد من أجله شرطُ CI الذي يرفض تخطّيه هناك: ظهر
 * العطل أول مرة شُغّل فيها.</p>
 */
class TenantSchemaResolverTest {

    /** السجلّ لا يُسأل في هذا المسار: الجواب يُعرف قبل أن تُترجم مؤسسةٌ إلى قاعدة */
    private final TenantSchemaResolver resolver =
            new TenantSchemaResolver(new ServerTenantContext(null), null);

    @Test
    void aThreadWithNoCentreGetsTheReservedIdentifierRatherThanAnException() {
        assertThatCode(resolver::resolveCurrentTenantIdentifier).doesNotThrowAnyException();

        assertThat(resolver.resolveCurrentTenantIdentifier())
                .isEqualTo(TenantSchemaResolver.NO_TENANT);
    }

    /**
     * والمعرّف المحجوز لا يمكن أن يكون اسم سنتر: {@code SchemaName} لا تقبل إلا حرفاً
     * صغيراً ثم حروفاً وأرقاماً وشرطات سفلية، والشرطة فيه هي ما يجعل التصادم مستحيلاً
     * لا نادراً.
     */
    @Test
    void theReservedIdentifierCanNeverBeARealSchemaName() {
        assertThat(TenantSchemaResolver.NO_TENANT).contains("-");
    }
}
