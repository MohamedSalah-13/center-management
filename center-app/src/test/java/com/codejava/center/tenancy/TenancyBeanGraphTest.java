package com.codejava.center.tenancy;

import com.codejava.center.config.tenancy.TenancyConfig;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantSweep;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * عقدٌ واحد، مرشَّحٌ أوّليّ واحد.
 *
 * <p>Spring يرفض الحقن حين يجد أكثر من {@code @Primary} لنوع واحد
 * ({@code more than one 'primary' bean found}) - وذلك رفضٌ وقت الإقلاع، لا وقت
 * الترجمة. ووقتُ الإقلاع هنا يعني: الخادم متعدّد المؤسسات لا يعمل عند من ينشره، إذ
 * السياق الوحيد الذي يشغّل هذه التهيئة هو اختبارٌ على حاوية يُتخطّى بلا Docker.</p>
 *
 * <p>ووقع ذلك فعلاً: {@code ServerTenantContext} يلبّي {@link TenantContext}
 * و{@link TenantSweep} معاً، وكان بجواره bean ثانٍ {@code @Primary} يعيده باسم
 * الثاني. فصارا مرشَّحين أوّليّين لنوعٍ واحد.</p>
 *
 * <p>والفحص على التهيئة لا على سياقٍ مُقلَع عن قصد: إقلاعُ هذا المسار يحتاج قاعدة
 * منصة بلهجة MySQL، وهو ما يعيد الاختبار إلى الحاوية التي نهرب منها. ما يُفحص هنا
 * هو القاعدة نفسها التي كُسرت، وبلا شيء.</p>
 */
class TenancyBeanGraphTest {

    /** العقود التي يقرؤها من يحقن، لا كل نوع في التهيئة */
    private static final List<Class<?>> CONTRACTS =
            List.of(TenantContext.class, TenantSweep.class, CurrentActor.class);

    @Test
    void noContractHasTwoPrimaryBeans() {
        for (Class<?> contract : CONTRACTS) {
            assertThat(primaryBeansAnswering(contract))
                    .as("مرشَّحان أوّليّان لـ " + contract.getSimpleName()
                            + ": الخادم لا يُقلع، ولا يظهر ذلك إلا عند من ينشره")
                    .hasSizeLessThanOrEqualTo(1);
        }
    }

    private List<String> primaryBeansAnswering(Class<?> contract) {
        List<String> names = new ArrayList<>();
        for (Method method : TenancyConfig.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)
                    && method.isAnnotationPresent(Primary.class)
                    && contract.isAssignableFrom(method.getReturnType())) {
                names.add(method.getName());
            }
        }
        return names;
    }
}
