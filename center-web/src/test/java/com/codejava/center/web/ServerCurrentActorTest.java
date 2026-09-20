package com.codejava.center.web;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.domain.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * هوية المنفّذ على خادم.
 *
 * <p>العطل الذي يمنعه هذا الصنف هو الأول في قائمة ما يجب إغلاقه قبل أي منفذ HTTP:
 * هويةٌ واحدة لكل العملية تعني أن آخر من سجّل دخوله يقرّر ما يراه من يطلب في اللحظة
 * نفسها. ولا يظهر في اختبار بمستخدم واحد.</p>
 */
class ServerCurrentActorTest {

    private final ServerCurrentActor actor = new ServerCurrentActor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thereIsNoActorBeforeAnybodyIsAuthenticated() {
        assertThat(actor.currentActor())
                .as("لا مستخدم مجهول ولا دور افتراضي: المصادقة إمّا وقعت وإمّا لم تقع")
                .isNull();
    }

    /**
     * البادئة {@code ROLE_} عرفُ إطار الأمان، و{@code Role} في هذا البرنامج
     * {@code ADMIN}. تسريبها إلى طبقة الأعمال يجعل الحارس يقارن نصّين لا يتطابقان
     * أبداً - فيرفض كل شيء، أو يقبل كل شيء لو قُلبت المقارنة.
     */
    @Test
    void theRoleArrivesWithoutTheFrameworksPrefix() {
        authenticateAs("reception", "ROLE_" + Role.SECRETARY.name());

        ActorIdentity identity = actor.currentActor();

        assertThat(identity).isNotNull();
        assertThat(identity.username()).isEqualTo("reception");
        assertThat(identity.role()).isEqualTo(Role.SECRETARY.name());
        assertThat(identity.hasRole(Role.SECRETARY.name())).isTrue();
        assertThat(identity.hasRole(Role.ADMIN.name())).isFalse();
    }

    @Test
    void aRoleWrittenWithoutThePrefixIsTakenAsItIs() {
        authenticateAs("owner", Role.ADMIN.name());

        assertThat(actor.currentActor().role()).isEqualTo(Role.ADMIN.name());
    }

    /** المدى هو الطلب: ما يُمحى من السياق يختفي من الهوية في اللحظة نفسها */
    @Test
    void theActorDisappearsWithTheContextThatCarriedIt() {
        authenticateAs("owner", Role.ADMIN.name());
        assertThat(actor.currentActor()).isNotNull();

        SecurityContextHolder.clearContext();

        assertThat(actor.currentActor()).isNull();
    }

    private void authenticateAs(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }
}
