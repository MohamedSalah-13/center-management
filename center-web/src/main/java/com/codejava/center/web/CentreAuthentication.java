package com.codejava.center.web;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.domain.enums.Role;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * من صاحب هذا الطلب، ولأيّ سنتر.
 *
 * <p>الهويةُ وحدها لا تكفي على منصة: اسمُ المستخدم فريدٌ <b>داخل قاعدة مؤسسته</b> لا
 * عبر المنصة، فقد يكون في عشرة سناتر عشرةُ حسابات باسم {@code admin}. ما يميّز أحدها
 * هو الاسم <b>ومؤسسته معاً</b>، ولذلك يحمل هذا الرمز الاثنين.</p>
 *
 * <p>والمؤسسة تُحفظ في الجلسة عند الدخول ولا تُقرأ من الطلب بعده. لو قُرئت من ترويسة
 * أو مسار لكان تبديلُ رقمٍ في الرابط كافياً ليقرأ موظفُ سنترٍ بيانات سنترٍ آخر - وهو
 * ما تحذّر منه {@link com.codejava.center.core.tenant.TenantContext} حرفياً: "لا يجوز
 * أن يأتي من قيمة يختارها العميل من دون التحقق من عضويته".</p>
 *
 * <p>و{@code principal} من نوع {@link ActorIdentity} لا نصّاً: {@link ServerCurrentActor}
 * يقرؤه كما هو فيصل معرّف المستخدم إلى سجل المراقبة، ولولاه لكان السطر يحمل اسماً
 * بلا رقم.</p>
 */
public class CentreAuthentication extends AbstractAuthenticationToken {

    /** ما يسبق اسم الدور في Spring Security */
    private static final String ROLE_PREFIX = "ROLE_";

    private final ActorIdentity identity;

    private final TenantId tenant;

    public CentreAuthentication(ActorIdentity identity, TenantId tenant) {
        super(List.of(new SimpleGrantedAuthority(ROLE_PREFIX + identity.role())));
        this.identity = identity;
        this.tenant = tenant;
        setAuthenticated(true);
    }

    public static CentreAuthentication of(Long userId, String username, Role role, TenantId tenant) {
        return new CentreAuthentication(new ActorIdentity(userId, username, role.name()), tenant);
    }

    public TenantId tenant() {
        return tenant;
    }

    @Override
    public Object getPrincipal() {
        return identity;
    }

    /**
     * {@code null} دائماً: كلمة المرور لا تُحفظ في الجلسة بعد التحقق منها.
     *
     * <p>الجلسة تُسلسَل إلى ذاكرة أو إلى مخزن جلسات، وما يُحفظ فيها يخرج معها.</p>
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return identity.username();
    }
}
