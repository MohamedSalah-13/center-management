package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // سيقوم Lombok بحقنها تلقائياً
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // استخدام matches لمقارنة النص الصريح (password) مع النص المشفر (user.getPassword())
            if (passwordEncoder.matches(password, user.getPassword())) {
                auditService.recordAs(user.getUsername(), user.getRole(),
                        AuditAction.LOGIN_SUCCEEDED, true, null);
                return user;
            }
        }

        // السبب يُفصَّل: اسم غير موجود يعني تخميناً، وكلمة مرور خاطئة على حساب قائم
        // تعني محاولة على حساب بعينه - والفرق بينهما هو ما يفيد صاحب السنتر
        auditService.recordAs(username, userOpt.map(User::getRole).orElse(null),
                AuditAction.LOGIN_FAILED, false,
                userOpt.isPresent() ? "reason=wrong-password" : "reason=unknown-user");

        throw new IllegalArgumentException(I18n.get("error.auth.invalidCredentials"));
    }

    /**
     * يسجّل خروج مستخدم.
     *
     * <p>يأخذ المستخدم صراحةً لا من {@code UserSession}: شاشة لوحة القيادة تُفرغ الجلسة
     * أولاً حتى لا يرث المستخدم التالي صلاحيات السابق، فلا يبقى ما يُنسب إليه الحدث.</p>
     */
    public void recordLogout(User user) {
        if (user == null) {
            return;
        }
        auditService.recordAs(user.getUsername(), user.getRole(), AuditAction.LOGGED_OUT, true, null);
    }
}
