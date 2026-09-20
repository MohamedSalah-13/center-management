package com.codejava.center.web;

import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * خادمٌ لسنترٍ واحد: قاعدة واحدة، ولا سجلّ منصة.
 *
 * <p>هذه ليست حالةً اختبارية بل نشرٌ حقيقي: سنترٌ يريد شاشاته على الشبكة - تليفون عند
 * البوابة، وجهاز في المكتب - بلا أن يشترك في منصة. وهو أيضاً ما يجعل هذه الوحدة
 * قابلةً للاختبار على H2 بلا قاعدة منصة ولا حاوية.</p>
 *
 * <p>والشرط مقلوبُ شرط {@code TenancyConfig} حرفاً بحرف، لا
 * {@code @ConditionalOnMissingBean}: الأخير يعتمد على ترتيب قراءة التهيئات، وترتيبٌ
 * يتغيّر بإصدار يعني خادماً متعدّد المؤسسات يُقلع بمستأجرٍ واحد ثابت - أي كل السناتر
 * تقرأ قاعدةً واحدة، وهو أسوأ ما يمكن أن يقع هنا.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class SingleCentreConfig {

    /**
     * المؤسسة الوحيدة في هذه القاعدة.
     *
     * <p>{@link TenantId#DESKTOP} اسمٌ ورثه المعرّف من أول موضع استُعمل فيه، ومعناه
     * "السنتر الوحيد في هذه القاعدة" - سواء كانت على مكتب أو خلف خادم. الصفّ نفسه في
     * {@code tenants} الذي زرعه {@code V16}.</p>
     */
    @Bean
    public TenantContext singleCentreContext() {
        return () -> TenantId.DESKTOP;
    }

    @Bean
    public TenantSweep singleCentreSweep(TenantContext tenantContext) {
        return new TenantSweep() {
            @Override
            public void sweep(Consumer<TenantId> work) {
                work.accept(tenantContext.currentTenant());
            }

            /** الاستثناء يصعد كما هو: تنبيه {@code BACKUP_FAILED} مبنيّ على أن المجدوِل يرى فشله */
            @Override
            public void within(TenantId tenant, Runnable work) {
                work.run();
            }
        };
    }

    /** لا سؤال: الخيط داخل المؤسسة الوحيدة دائماً، سواء جاء من طلب أو من مجدوِل */
    @Bean
    public CurrentCentre singleCurrentCentre() {
        return () -> TenantId.DESKTOP;
    }

    /** لا سجلّ منصة يُسأل: القاعدة واحدة، وما يُكتب في حقل السنتر يُتجاهل */
    @Bean
    public CentreDirectory singleCentreDirectory() {
        return slug -> TenantId.DESKTOP;
    }
}
