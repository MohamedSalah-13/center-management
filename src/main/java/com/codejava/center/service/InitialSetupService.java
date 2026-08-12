package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * بوابة التهيئة الوحيدة لقاعدة مستخدمين جديدة.
 *
 * <p>الشرط هو فراغ جدول المستخدمين كله، لا غياب اسم {@code admin}: حذف حساب المدير
 * لاحقاً لا يجوز أن يفتح باب إنشاء مدير بلا تسجيل دخول. ويُعاد التحقق داخل معاملة
 * الإنشاء نفسها؛ إخفاء الشاشة وحده ليس حدّ أمان.</p>
 */
@Service
@RequiredArgsConstructor
public class InitialSetupService {

    public static final String INITIAL_ADMIN_USERNAME = "admin";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        return userRepository.count() == 0;
    }

    @Transactional
    public User createInitialAdmin(String password, String confirmation) {
        if (userRepository.count() != 0) {
            throw new IllegalStateException(I18n.get("setup.error.alreadyCompleted"));
        }

        validatePassword(password, confirmation);

        try {
            User admin = userRepository.saveAndFlush(User.builder()
                    .username(INITIAL_ADMIN_USERNAME)
                    .password(passwordEncoder.encode(password))
                    .role(Role.ADMIN)
                    .build());

            // لا جلسة قبل أول دخول؛ actor=null يعني أن النظام أنشأ الحساب.
            // record تشارك هذه المعاملة، فيتراجع السطر إذا تراجع إنشاء المستخدم.
            auditService.record(AuditAction.USER_CREATED, admin.getId(), admin.getUsername(),
                    "role=ADMIN; initialSetup=true");
            return admin;
        } catch (DataIntegrityViolationException e) {
            // يحمي من تشغيل نسختين من التطبيق في اللحظة نفسها على قاعدة فارغة.
            throw new IllegalStateException(I18n.get("setup.error.alreadyCompleted"), e);
        }
    }

    private void validatePassword(String password, String confirmation) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(I18n.get("setup.error.passwordRequired"));
        }
        PasswordPolicy.validate(password);
        PasswordPolicy.requireConfirmation(password, confirmation);
    }
}
