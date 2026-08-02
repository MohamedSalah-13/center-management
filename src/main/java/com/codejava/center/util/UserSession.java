package com.codejava.center.util;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import org.springframework.stereotype.Component;

/**
 * جلسة المستخدم الحالي.
 * صارت bean بدلاً من حقل static حتى يمكن حقنها في الـ Aspect والخدمات واختبارها،
 * بدلاً من الوصول الساكن الذي لا يمكن استبداله في الاختبارات.
 * volatile لأن الجلسة تُقرأ من خيوط خلفية (CompletableFuture) وتُكتب من خيط الواجهة.
 */
@Component
public class UserSession {

    private volatile User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void cleanUserSession() {
        this.currentUser = null;
    }

    public boolean hasRole(Role role) {
        User user = this.currentUser;
        return user != null && user.getRole() == role;
    }
}
