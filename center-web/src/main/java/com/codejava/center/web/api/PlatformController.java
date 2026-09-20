package com.codejava.center.web.api;

import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.User;
import com.codejava.center.platform.PlatformTenant;
import com.codejava.center.platform.TenantProvisioning;
import com.codejava.center.platform.TenantRegistry;
import com.codejava.center.platform.TenantStatus;
import com.codejava.center.web.EdgeThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * فتحُ سنترٍ على المنصة، وتفعيلُ دعوته.
 *
 * <p>كان {@code TenantProvisioning} يعمل من Java وحدها: المنصة تُقلع وتوجّه وتعزل، ولا
 * سبيل إلى إدخال عميلٍ فيها إلا بكتابة كود. وهذا الملف هو الفارق بين "يعمل تقنياً"
 * و"يمكن أن يُستعمل".</p>
 *
 * <p>ولا منطق هنا كعادة هذه الوحدة: الخطوات - إنشاء القاعدة، صفّ السجلّ، الترحيل، زرع
 * الإعدادات، إصدار الدعوة - كلّها في {@code TenantProvisioning} بترتيبها الذي يهمّ.</p>
 *
 * <h2>هويتان لا واحدة</h2>
 *
 * <p>ما تحت {@code /api/platform} يحرسه {@code PlatformOperatorFilter} برمز مشغّل
 * المنصة - وهو ليس موظفاً في سنتر ولا حساباً في قاعدة. إلا
 * {@link #redeemInvite} فهي للعميل الجديد نفسه: لا رمز معه ولا حساب بعد، ودليلُه رمزُ
 * الدعوة. ولذلك وحدها محروسةٌ بحاجز العنوان: رمزٌ عشوائي بمئة وستين بتاً لا يُخمَّن،
 * لكنّ بابَ تخمينٍ بلا عدّاد يبقى باباً.</p>
 */
@RestController
@RequestMapping("/api/platform")
@ConditionalOnProperty(prefix = "center.tenancy", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlatformController {

    private final TenantProvisioning provisioning;
    private final TenantRegistry registry;
    private final EdgeThrottle edgeThrottle;

    /**
     * @param schema اسمُ قاعدة المؤسسة، مكتوباً لا مشتقاً من الاسم المقروء: هو اسمٌ في
     *               خادم قواعد يعيش أطول من الاسم التجاري، وتغييرُه لاحقاً نقلُ بيانات
     */
    public record OpenCentreRequest(@NotBlank String name, @NotBlank String slug,
                                    @NotBlank String schema) {
    }

    public record CentreView(long id, String name, String slug, String schema, String status) {
    }

    /** رمزُ الدعوة يظهر مرةً واحدة: لا يُخزَّن إلا بصمته، فلا سبيل إلى عرضه ثانيةً */
    public record OpenedCentre(CentreView centre, String inviteCode) {
    }

    public record RedeemRequest(@NotBlank String code, @NotBlank String password,
                                @NotBlank String confirmation) {
    }

    public record AdminView(String username) {
    }

    public record StatusRequest(@NotNull TenantStatus status) {
    }

    @PostMapping("/centres")
    public OpenedCentre openCentre(@RequestBody OpenCentreRequest request) {
        TenantProvisioning.NewTenant opened = provisioning.open(
                request.name().trim(), request.slug().trim(), SchemaName.of(request.schema().trim()));
        return new OpenedCentre(view(opened.tenant()), opened.inviteCode());
    }

    @GetMapping("/centres")
    public List<CentreView> centres() {
        return registry.all().stream().map(PlatformController::view).toList();
    }

    /**
     * إيقافُ اشتراكٍ أو إغلاقُ سنتر.
     *
     * <p>الموقوف يبقى بقاعدته كاملة ولا يُخدَم: لا نسخة ليلية ولا فحص تنبيهات ولا دخول.
     * وذلك مقصود - انقطاعُ الدفع ليس سبباً لمحو بيانات سنتر.</p>
     */
    @PostMapping("/centres/{id}/status")
    public CentreView changeStatus(@PathVariable long id, @RequestBody StatusRequest request) {
        TenantId tenant = new TenantId(id);
        provisioning.changeStatus(tenant, request.status());
        return registry.find(tenant)
                .map(PlatformController::view)
                .orElseThrow(() -> new IllegalArgumentException("unknown centre"));
    }

    /**
     * الدعوة تُفعَّل مرةً فتفتح حساب المدير الأول في مؤسسته وحدها.
     *
     * <p>والترتيب داخل الخدمة مقصود: الحساب يُنشأ ثم تُستهلك الدعوة. "حسابٌ صُنع ورمزٌ
     * ما زال حيّاً" يغلقه فحصُ "الجدول فارغ"، بينما "رمزٌ استُهلك ولا حساب" يترك سنتراً
     * لا يدخله أحد.</p>
     */
    @PostMapping("/invites/redeem")
    public AdminView redeemInvite(@RequestBody RedeemRequest request, HttpServletRequest http) {
        edgeThrottle.refuseIfLocked(http);
        try {
            User admin = provisioning.redeemInvite(
                    request.code().trim(), request.password(), request.confirmation());
            edgeThrottle.recordSuccess(http);
            return new AdminView(admin.getUsername());
        } catch (RuntimeException refused) {
            edgeThrottle.recordFailure(http);
            throw refused;
        }
    }

    private static CentreView view(PlatformTenant tenant) {
        return new CentreView(tenant.id().value(), tenant.name(), tenant.slug(),
                tenant.schema().value(), tenant.status().name());
    }
}
