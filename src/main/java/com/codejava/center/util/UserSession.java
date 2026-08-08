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

    private volatile boolean dayBriefingShown;

    public void setCurrentUser(User user) {
        this.currentUser = user;
        this.dayBriefingShown = false;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void cleanUserSession() {
        this.currentUser = null;
        this.dayBriefingShown = false;
    }

    /**
     * موجز اليوم يُعرض مرة واحدة لكل دخول: أول من يطلبه يأخذه، ومن بعده لا شيء.
     *
     * <p>الحارس هنا لا في المتحكّم لأن {@code DashboardController} نموذج
     * ({@code PROTOTYPE}) يُعاد بناؤه مع كل تبديل للغة أو لحجم الخط، فحقلٌ بداخله يولد
     * فارغاً في كل مرة وتقفز البطاقة من جديد على من غيّر اللغة فحسب. والجلسة هي المدى
     * الصحيح: تبديل اللغة ليس دخولاً جديداً، وتسجيل الخروج ثم الدخول هو دخول جديد
     * فعلاً - وقد يكون اليوم تغيّر بينهما.</p>
     */
    public boolean claimDayBriefing() {
        if (dayBriefingShown) {
            return false;
        }
        this.dayBriefingShown = true;
        return true;
    }

    public boolean hasRole(Role role) {
        User user = this.currentUser;
        return user != null && user.getRole() == role;
    }
}
