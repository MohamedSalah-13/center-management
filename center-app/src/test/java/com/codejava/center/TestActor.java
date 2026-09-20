package com.codejava.center;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.core.security.CurrentActor;
import com.codejava.center.core.tenant.TenantContext;
import com.codejava.center.core.tenant.TenantId;
import com.codejava.center.core.tenant.TenantSweep;
import com.codejava.center.domain.User;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * من ينفّذ العملية، في الاختبارات.
 *
 * <p>كانت هذه الاختبارات تحقن {@code util/TestActor} — جلسةَ تطبيق JavaFX — لتمرّ من
 * {@code @RequiresRole}. وذلك بالضبط ما يمنعه فصلُ هذه الوحدة: طبقةُ الأعمال لا ترى
 * جلسةَ نافذة، ولا يصحّ أن تراها اختباراتُها. البديلُ ليس حيلةً بل هو العقد نفسه —
 * {@link CurrentActor} من النواة — مركَّباً بأبسط ما يكون.</p>
 *
 * <p>وهو أيضاً ما يجعل الاختبار أصدق: ما يُفحص هنا هو أن الحارس يقرأ الدور من العقد، لا
 * أن جلسةً بعينها تعمل. تركيبُ العقد على الخادم يختلف، والحارس لا يعلم.</p>
 */
@Component
public class TestActor implements CurrentActor, TenantContext, TenantSweep {

    private volatile User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void cleanUserSession() {
        this.currentUser = null;
    }

    @Override
    public ActorIdentity currentActor() {
        User user = currentUser;
        return user == null ? null
                : new ActorIdentity(user.getId(), user.getUsername(), user.getRole().name());
    }

    @Override
    public TenantId currentTenant() {
        return TenantId.DESKTOP;
    }

    /**
     * مؤسسةٌ واحدة، كما على الجهاز: العمل يُنفَّذ كما هو.
     *
     * <p>والاستثناء يصعد ولا يُبتلع: اختبارٌ يبتلع خطأً داخل دورة مجدوِل يمرّ أخضر
     * على كود مكسور.</p>
     */
    @Override
    public void sweep(Consumer<TenantId> work) {
        work.accept(currentTenant());
    }

    @Override
    public void within(TenantId tenant, Runnable work) {
        work.run();
    }
}
