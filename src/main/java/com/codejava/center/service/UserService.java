package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    @RequiresRole(Role.ADMIN)
    public User saveUser(User user, String rawPassword) {
        // التحقق من أن اسم المستخدم غير مكرر عند إضافة مستخدم جديد
        if (user.getId() == null) {
            Optional<User> existingUser = userRepository.findByUsername(user.getUsername());
            if (existingUser.isPresent()) {
                throw new IllegalStateException(I18n.get("error.user.usernameTaken"));
            }
        }

        // إذا تم إدخال كلمة مرور جديدة، قم بتشفيرها
        boolean passwordChanged = rawPassword != null && !rawPassword.trim().isEmpty();
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        } else if (user.getId() == null) {
            // لا يمكن إضافة مستخدم جديد بدون كلمة مرور
            throw new IllegalArgumentException(I18n.get("error.user.passwordRequired"));
        }

        boolean isNew = user.getId() == null;
        User saved = userRepository.save(user);

        // منح صلاحية المدير لحساب وتغيير كلمة مرور حساب قائم هما أخطر ما في هذه الشاشة:
        // كلاهما يُقرأ من السطر دون فتح جدول المستخدمين. كلمة المرور نفسها لا تُسجَّل بحال.
        auditService.record(isNew ? AuditAction.USER_CREATED : AuditAction.USER_UPDATED,
                saved.getId(), saved.getUsername(),
                "role=" + saved.getRole().name() + "; passwordChanged=" + passwordChanged);

        return saved;
    }

    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    @RequiresRole(Role.ADMIN)
    public void deleteUser(Long userId) {
        // الاسم يُقرأ قبل الحذف: بعده لا يبقى في الجدول ما يُنسب إليه السطر
        Optional<User> target = userRepository.findById(userId);

        userRepository.deleteById(userId);
        auditService.record(AuditAction.USER_DELETED, userId,
                target.map(User::getUsername).orElse(null),
                target.map(user -> "role=" + user.getRole().name()).orElse(null));
    }
}
