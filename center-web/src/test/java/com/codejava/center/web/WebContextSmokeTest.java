package com.codejava.center.web;

import com.codejava.center.core.backup.BackupTarget;
import com.codejava.center.core.print.SheetHeaderPolicy;
import com.codejava.center.core.secret.BackupSecretStore;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.core.ui.UiDispatcher;
import com.codejava.center.service.notification.MessagingLinkPreferences;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * يُقلع الخادم بكل منافذه مركَّبة.
 *
 * <p>{@code center-app} مكتبة، ومنافذها تُترك بلا تنفيذ فيها عمداً: {@code CurrentActor}
 * و{@code TenantContext} و{@code BackupTarget} وأخواتها يجيب عنها الطرفُ الذي يشغّلها.
 * فَنَقصُ واحدٍ منها لا يُسقط اختباراً في تلك الوحدة - يُسقط الإقلاع هنا وحده، وعند
 * من ينشر إن لم يوجد هذا الاختبار.</p>
 *
 * <p>وهو الدرس نفسه الذي كتبه {@code ApplicationContextSmokeTest} على الجهاز حين مرّ
 * {@code @ConditionalOnMissingBean} المعطوب عبر واحد وخمسين اختباراً.</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class WebContextSmokeTest {

    @Autowired private ApplicationContext context;

    @Test
    void everyPortTheBusinessLayerAsksForHasAnAnswerHere() {
        assertThat(context.getBean(CurrentActor.class)).isInstanceOf(ServerCurrentActor.class);
        assertThat(context.getBean(BackupSecretStore.class)).isInstanceOf(EnvironmentSecrets.class);
        assertThat(context.getBean(BackupTarget.class)).isInstanceOf(ServerBackupTarget.class);
        assertThat(context.getBean(SheetHeaderPolicy.class)).isNotNull();
        assertThat(context.getBean(UiDispatcher.class)).isNotNull();
        assertThat(context.getBean(MessagingLinkPreferences.class)).isNotNull();
    }

    /**
     * الترويسة تُطبع دائماً على هذا الطرف.
     *
     * <p>سببُ إطفائها على الجهاز ورقٌ مطبوعٌ عليه ترويسة في درج <b>تلك</b> الطابعة،
     * ولا درج هنا: الملف يُنزَّل ويُحفظ ويُرسل، وبلا ترويسة لا يُعرف صاحبه.</p>
     */
    @Test
    void sheetsCarryTheCentreLetterhead() {
        assertThat(context.getBean(SheetHeaderPolicy.class).printsCenterHeader()).isTrue();
    }

    /**
     * تعدّدية المؤسسات مطفأة هنا، فالمؤسسة واحدة ثابتة - وهو نشرٌ حقيقي لا حالة
     * اختبارية: سنترٌ يريد شاشاته على شبكته بلا أن يشترك في منصة.
     */
    @Test
    void aSingleCentreInstallNeedsNoPlatformRegistry() {
        assertThat(context.getBean(TenantContext.class).currentTenant()).isEqualTo(TenantId.DESKTOP);
        assertThat(context.getBean(TenantSweep.class)).isNotNull();
        assertThat(context.getBean(CurrentCentre.class).boundOrNull()).isEqualTo(TenantId.DESKTOP);
    }

    /**
     * العمل الخلفي يحمل هوية من طلبه.
     *
     * <p>ولولا هذا الاختبار لبقي اللفّ حبراً: نسيانُه لا يُسقط شيئاً حتى يُسلَّم عملٌ
     * إلى الخلفية من داخل طلب، فيُرفض بحجّة "لا جلسة" - ورسالةٌ تقول ذلك لمن هو داخلٌ
     * فعلاً لا يُبحث عن سببها في مجمّع خيوط.</p>
     */
    @Test
    void backgroundWorkKeepsTheIdentityOfWhoeverAskedForIt() {
        assertThat(context.getBean("applicationTaskExecutor", AsyncTaskExecutor.class))
                .isInstanceOf(DelegatingSecurityContextAsyncTaskExecutor.class);
    }

    /**
     * المنفذان المركَّبان <b>ثابتاً</b> لا يُحقنان، فلا شيء يفشل حين يُنسى تركيبهما:
     * اللغة تعود عربية والعملة جنيهاً، وهو ما كان سيراه أغلب العملاء على أي حال.
     *
     * <p>ولذلك يُفحص المركِّب لا الجواب: نسيانُ السطر يظهر عند سنتر سعودي إيصالاتُه
     * بالجنيه وكلُّ رقم فيها صحيح.</p>
     */
    @Test
    void theStaticallyInstalledPortsAreThisModulesOwn() {
        assertThat(I18n.provider()).isInstanceOf(ServerLocaleProvider.class);
        assertThat(MoneyUtils.provider()).isInstanceOf(ServerCurrencyProvider.class);
    }
}
