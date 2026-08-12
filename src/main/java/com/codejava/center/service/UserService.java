package com.codejava.center.service;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PasswordPolicy;
import com.codejava.center.util.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_USERNAME_LENGTH = 50;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserSession userSession;

    @Transactional
    @RequiresRole(Role.ADMIN)
    public User saveUser(User request, String rawPassword, String passwordConfirmation) {
        validateRequest(request);

        // قفل قصير لكل إدارة المستخدمين: القرار الأمني يعتمد على عدد المديرين الحاليين.
        List<User> users = userRepository.findAllForUpdate();
        boolean isNew = request.getId() == null;
        String username = request.getUsername().trim();

        rejectDuplicateUsername(users, request.getId(), username);

        User target = isNew
                ? new User()
                : users.stream()
                .filter(candidate -> candidate.getId().equals(request.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(I18n.get("error.user.notFound")));

        Role previousRole = target.getRole();
        String previousUsername = target.getUsername();
        if (!isNew) {
            protectAdministratorIdentity(target, username, request.getRole(), users);
        }

        boolean passwordChanged = rawPassword != null && !rawPassword.isBlank();
        if (isNew && !passwordChanged) {
            throw new IllegalArgumentException(I18n.get("error.user.passwordRequired"));
        }
        if (passwordChanged) {
            PasswordPolicy.validate(rawPassword);
            PasswordPolicy.requireConfirmation(rawPassword, passwordConfirmation);
            target.setPassword(passwordEncoder.encode(rawPassword));
        }

        target.setUsername(username);
        target.setRole(request.getRole());

        User saved;
        try {
            saved = userRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException e) {
            // الحارس النهائي لسباق إنشاء اسم واحد من نافذتين/جهازين.
            throw new IllegalStateException(I18n.get("error.user.usernameTaken"), e);
        }

        // منح صلاحية المدير لحساب وتغيير كلمة مرور حساب قائم هما أخطر ما في هذه الشاشة:
        // كلاهما يُقرأ من السطر دون فتح جدول المستخدمين. كلمة المرور نفسها لا تُسجَّل بحال.
        auditService.record(isNew ? AuditAction.USER_CREATED : AuditAction.USER_UPDATED,
                saved.getId(), saved.getUsername(),
                isNew
                        ? "role=" + saved.getRole().name() + "; passwordChanged=true"
                        : "usernameBefore=" + previousUsername
                        + "; roleBefore=" + previousRole.name()
                        + "; roleAfter=" + saved.getRole().name()
                        + "; passwordChanged=" + passwordChanged);

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
        List<User> users = userRepository.findAllForUpdate();
        User target = users.stream()
                .filter(candidate -> candidate.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(I18n.get("error.user.notFound")));

        if (isBuiltInAdmin(target)) {
            throw new IllegalStateException(I18n.get("user.cannotDeleteAdmin"));
        }
        if (target.getRole() == Role.ADMIN && adminCount(users) <= 1) {
            throw new IllegalStateException(I18n.get("error.user.lastAdmin"));
        }
        if (isCurrentUser(target)) {
            throw new IllegalStateException(I18n.get("error.user.cannotDeleteCurrent"));
        }

        // الاسم يُقرأ قبل الحذف: بعده لا يبقى في الجدول ما يُنسب إليه السطر
        userRepository.delete(target);
        auditService.record(AuditAction.USER_DELETED, userId, target.getUsername(),
                "role=" + target.getRole().name());
    }

    private void validateRequest(User request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException(I18n.get("error.user.usernameRequired"));
        }
        if (request.getUsername().trim().length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException(I18n.format(
                    "error.user.usernameTooLong", MAX_USERNAME_LENGTH));
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException(I18n.get("error.user.roleRequired"));
        }
    }

    private void rejectDuplicateUsername(List<User> users, Long requestId, String username) {
        boolean duplicate = users.stream().anyMatch(candidate ->
                !candidate.getId().equals(requestId)
                        && candidate.getUsername().equalsIgnoreCase(username));
        if (duplicate) {
            throw new IllegalStateException(I18n.get("error.user.usernameTaken"));
        }
    }

    private void protectAdministratorIdentity(User target, String username, Role requestedRole,
                                                List<User> users) {
        if (isBuiltInAdmin(target)) {
            if (!InitialSetupService.INITIAL_ADMIN_USERNAME.equals(username)) {
                throw new IllegalStateException(I18n.get("error.user.cannotRenameAdmin"));
            }
            if (requestedRole != Role.ADMIN) {
                throw new IllegalStateException(I18n.get("error.user.cannotDemoteAdmin"));
            }
        }

        if (target.getRole() == Role.ADMIN && requestedRole != Role.ADMIN
                && adminCount(users) <= 1) {
            throw new IllegalStateException(I18n.get("error.user.lastAdmin"));
        }

        boolean identityChanged = !target.getUsername().equals(username)
                || target.getRole() != requestedRole;
        if (identityChanged && isCurrentUser(target)) {
            throw new IllegalStateException(I18n.get("error.user.cannotChangeOwnIdentity"));
        }
    }

    private long adminCount(List<User> users) {
        return users.stream().filter(user -> user.getRole() == Role.ADMIN).count();
    }

    private boolean isBuiltInAdmin(User user) {
        return InitialSetupService.INITIAL_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername());
    }

    private boolean isCurrentUser(User target) {
        User current = userSession.getCurrentUser();
        return current != null && current.getId() != null && current.getId().equals(target.getId());
    }
}
