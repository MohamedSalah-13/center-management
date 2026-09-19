package com.codejava.center.core.security;

/**
 * مصدر هوية المنفّذ الحالي لطبقة الأعمال.
 *
 * <p>Desktop يربطه بجلسة JavaFX، وخادم SaaS سيربطه بطلب HTTP وSpring Security.
 * غياب الهوية يعني عملية نظامية أو عدم وجود جلسة، بحسب حالة الاستخدام.</p>
 */
public interface CurrentActor {

    ActorIdentity currentActor();

    default boolean hasRole(String role) {
        ActorIdentity actor = currentActor();
        return actor != null && actor.hasRole(role);
    }
}
