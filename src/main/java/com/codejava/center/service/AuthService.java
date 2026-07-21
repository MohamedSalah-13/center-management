package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.repository.UserRepository;
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

    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // استخدام matches لمقارنة النص الصريح (password) مع النص المشفر (user.getPassword())
            if (passwordEncoder.matches(password, user.getPassword())) {
                return user;
            }
        }
        throw new IllegalArgumentException("اسم المستخدم أو كلمة المرور غير صحيحة.");
    }
}