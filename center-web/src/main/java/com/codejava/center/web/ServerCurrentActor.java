package com.codejava.center.web;

import com.codejava.center.core.security.ActorIdentity;
import com.codejava.center.core.security.CurrentActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * من ينفّذ العملية على خادم: هويةُ <b>هذا الطلب</b>، لا هوية البرنامج.
 *
 * <p>{@code UserSession} على الجهاز حقلٌ واحد في العملية كلها، وهو الصواب هناك: برنامجٌ
 * أمام إنسان واحد. وعلى خادمٍ هو أخطر ما في القائمة - هويةٌ واحدة لكل الطلبات تعني أن
 * آخر من سجّل دخوله يقرّر ما يراه من يطلب في اللحظة نفسها. ولذلك المدى هنا هو الطلب،
 * ومصدرُه {@code SecurityContextHolder} الذي يملأه إطار الأمان عند المصادقة.</p>
 *
 * <p>والدور يُقرأ من الصلاحيات بعد نزع البادئة {@code ROLE_}: Spring Security يكتبها
 * وطبقةُ الأعمال لا تعرفها - {@code Role} في هذا البرنامج {@code ADMIN} لا
 * {@code ROLE_ADMIN}، وتسريبُ عرف الإطار إلى الأعمال يجعل الحارس يقارن نصّين لا
 * يتطابقان أبداً فيرفض كل شيء... أو يقبل كل شيء لو قُلبت المقارنة.</p>
 *
 * <p>{@code null} حين لا مصادقة، وهو ما يقرؤه {@code RoleEnforcementAspect} على أنه
 * "لا جلسة" فيرفض ويكتب السطر. لا مستخدم مجهول ولا دور افتراضي: المصادقة إمّا وقعت
 * وإمّا لم تقع.</p>
 */
public class ServerCurrentActor implements CurrentActor {

    /** ما يسبق اسم الدور في Spring Security ولا معنى له في طبقة الأعمال */
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public ActorIdentity currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            return null;
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(ServerCurrentActor::withoutPrefix)
                .findFirst()
                .orElse(null);
        if (role == null) {
            return null;
        }

        return new ActorIdentity(idOf(authentication), authentication.getName(), role);
    }

    private static String withoutPrefix(String authority) {
        return authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length()) : authority;
    }

    /**
     * معرّف المستخدم إن حمله الـ principal.
     *
     * <p>{@code null} مقبول: {@code ActorIdentity} يستعمله في سطر سجل المراقبة،
     * والسطر يحمل الاسم أصلاً - و{@code entityLabel} هو ما يقرؤه المراجع لا الرقم.</p>
     */
    private static Long idOf(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof ActorIdentity identity) {
            return identity.userId();
        }
        return null;
    }
}
