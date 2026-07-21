package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.repository.UserRepository;
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

    @Transactional
    public User saveUser(User user, String rawPassword) {
        // التحقق من أن اسم المستخدم غير مكرر عند إضافة مستخدم جديد
        if (user.getId() == null) {
            Optional<User> existingUser = userRepository.findByUsername(user.getUsername());
            if (existingUser.isPresent()) {
                throw new IllegalStateException("اسم المستخدم هذا مسجل مسبقاً.");
            }
        }

        // إذا تم إدخال كلمة مرور جديدة، قم بتشفيرها
        if (rawPassword != null && !rawPassword.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        } else if (user.getId() == null) {
            // لا يمكن إضافة مستخدم جديد بدون كلمة مرور
            throw new IllegalArgumentException("كلمة المرور مطلوبة للمستخدم الجديد.");
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}