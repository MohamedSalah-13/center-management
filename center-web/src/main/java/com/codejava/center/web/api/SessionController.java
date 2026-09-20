package com.codejava.center.web.api;

import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.domain.User;
import com.codejava.center.service.AuthService;
import com.codejava.center.util.I18n;
import com.codejava.center.web.CentreAuthentication;
import com.codejava.center.web.CentreDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;

/**
 * الدخول والخروج، وسؤال "من أنا".
 *
 * <p><b>الدخول هو الطلب الوحيد الذي يحلّ مؤسسته بنفسه.</b> بقيةُ الطلبات يربطها
 * {@code TenantBindingFilter} من الجلسة، والجلسةُ هنا لم تُخلق بعد - وجدول
 * {@code users} داخل قاعدة المؤسسة لا فوقها، فلا سبيل إلى البحث عن اسم المستخدم قبل
 * معرفة أيّ قاعدة تُسأل. ولهذا نموذج الدخول ثلاثةُ حقول لا اثنان.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

    private final AuthService authService;
    private final CentreDirectory centres;
    private final TenantSweep tenants;
    private final SecurityContextRepository contexts;

    public record LoginRequest(
            /* اسمُ السنتر؛ يُتجاهل على تركيبٍ يخدم سنتراً واحداً */
            String centre,
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record MeView(String username, String role, String roleName,
                         String locale, boolean rightToLeft) {
    }

    @PostMapping("/session")
    public MeView signIn(@RequestBody LoginRequest request,
                         HttpServletRequest httpRequest,
                         HttpServletResponse httpResponse) {

        TenantId tenant = centres.resolve(request.centre());

        // حاملٌ لأن TenantSweep.within يأخذ Runnable: الإطار كله - الدورة الليلية
        // والمجدوِل - لا يحتاج قيمةً عائدة، وتوسيعُ العقد في النواة لأجل مستدعٍ واحد
        // يفتح باب "أعطني معرّف المؤسسة" الذي أُغلق عمداً
        AtomicReference<User> signedIn = new AtomicReference<>();
        tenants.within(tenant, () ->
                signedIn.set(authService.authenticate(request.username(), request.password())));

        User user = signedIn.get();
        rotateSession(httpRequest);

        CentreAuthentication authentication = CentreAuthentication.of(
                user.getId(), user.getUsername(), user.getRole(), tenant);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, httpRequest, httpResponse);

        return view(user.getUsername(), user.getRole().name(), user.getRole().getDisplayName());
    }

    /**
     * الخروج: يُكتب في سجل المراقبة أولاً، ثم تُنهى الجلسة.
     *
     * <p>الترتيب مقصود - بعد إنهاء الجلسة لا يبقى من يُنسب إليه الحدث، وهو نفس سبب
     * أخذ {@code recordLogout} للمستخدم صراحةً بدل قراءته من الجلسة.</p>
     */
    @DeleteMapping("/session")
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof CentreAuthentication centre) {
            tenants.within(centre.tenant(), () -> authService.recordLogout(
                    userStub(centre)));
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeView me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        com.codejava.center.core.security.ActorIdentity identity =
                (com.codejava.center.core.security.ActorIdentity) authentication.getPrincipal();
        com.codejava.center.domain.enums.Role role =
                com.codejava.center.domain.enums.Role.valueOf(identity.role());
        return view(identity.username(), role.name(), role.getDisplayName());
    }

    private MeView view(String username, String role, String roleName) {
        return new MeView(username, role, roleName,
                I18n.current().toLanguageTag(), I18n.isRightToLeft());
    }

    /**
     * سطرُ سجل المراقبة يحتاج الاسم والدور، لا صفَّ المستخدم.
     *
     * <p>وقراءةُ الصفّ من القاعدة لأجل سطرٍ يحمل ما في الجلسة أصلاً استعلامٌ عند كل
     * خروج، ويفشل لمن حُذف حسابه بينما جلسته مفتوحة - فيضيع بالضبط الحدث الذي
     * يهمّ.</p>
     */
    private User userStub(CentreAuthentication centre) {
        com.codejava.center.core.security.ActorIdentity identity =
                (com.codejava.center.core.security.ActorIdentity) centre.getPrincipal();
        User user = new User();
        user.setUsername(identity.username());
        user.setRole(com.codejava.center.domain.enums.Role.valueOf(identity.role()));
        return user;
    }

    /**
     * تدوير معرّف الجلسة عند الدخول.
     *
     * <p>بدونه يبقى المعرّف الذي حمله المتصفّح قبل الدخول، وهو المعرّف الذي قد يكون
     * زرعه غيرُه - فيرث جلسةً صارت جلسةَ من سجّل دخوله توّاً. Spring يدوّره تلقائياً
     * حين تقع المصادقة داخل فلتره؛ وهي تقع هنا في متحكّم، فالتدوير علينا.</p>
     */
    private void rotateSession(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
    }
}
