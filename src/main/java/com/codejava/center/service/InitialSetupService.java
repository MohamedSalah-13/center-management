package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

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
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_BCRYPT_BYTES = 72;

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
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(I18n.format(
                    "setup.error.passwordTooShort", MIN_PASSWORD_LENGTH));
        }
        // BCrypt لا يميّز ما بعد 72 بايت؛ رفض القيمة يمنع كلمتين مختلفتين من التطابق.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException(I18n.get("setup.error.passwordTooLong"));
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException(I18n.get("setup.error.passwordMismatch"));
        }
    }
}
