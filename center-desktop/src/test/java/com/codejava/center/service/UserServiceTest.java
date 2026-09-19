package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({UserService.class, AuditService.class, UserSession.class, SecurityConfig.class})
class UserServiceTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSession userSession;
    @Autowired private PasswordEncoder passwordEncoder;

    private User builtInAdmin;

    @BeforeEach
    void createAdministratorSession() {
        builtInAdmin = persist("admin", Role.ADMIN);
        userSession.setCurrentUser(builtInAdmin);
    }

    @Test
    void builtInAdminCannotBeDeletedRenamedOrDemoted() {
        assertThatThrownBy(() -> userService.deleteUser(builtInAdmin.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> userService.saveUser(
                request(builtInAdmin.getId(), "renamed-admin", Role.ADMIN), "", ""))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> userService.saveUser(
                request(builtInAdmin.getId(), "admin", Role.SECRETARY), "", ""))
                .isInstanceOf(IllegalStateException.class);

        User unchanged = userRepository.findById(builtInAdmin.getId()).orElseThrow();
        assertThat(unchanged.getUsername()).isEqualTo("admin");
        assertThat(unchanged.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void builtInAdminMayChangeItsOwnPasswordWithoutChangingIdentity() {
        User saved = userService.saveUser(
                request(builtInAdmin.getId(), "admin", Role.ADMIN),
                "New-Admin-Pass-2026", "New-Admin-Pass-2026");

        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("New-Admin-Pass-2026", saved.getPassword())).isTrue();
    }

    @Test
    void lastAdministratorCannotBeDeletedOrDemotedEvenWithANonDefaultName() {
        userRepository.delete(builtInAdmin);
        User owner = persist("owner", Role.ADMIN);
        userSession.setCurrentUser(owner);

        assertThatThrownBy(() -> userService.deleteUser(owner.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> userService.saveUser(
                request(owner.getId(), "owner", Role.SECRETARY), "", ""))
                .isInstanceOf(IllegalStateException.class);

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void anotherAdministratorMayBeDemotedWhenOneAdministratorWillRemain() {
        User secondAdmin = persist("second-admin", Role.ADMIN);

        User saved = userService.saveUser(
                request(secondAdmin.getId(), "second-admin", Role.SECRETARY), "", "");

        assertThat(saved.getRole()).isEqualTo(Role.SECRETARY);
        assertThat(userRepository.findById(builtInAdmin.getId())).isPresent();
    }

    @Test
    void anotherAdministratorMayBeDeletedWhenOneAdministratorWillRemain() {
        User secondAdmin = persist("second-admin", Role.ADMIN);

        userService.deleteUser(secondAdmin.getId());

        assertThat(userRepository.findById(secondAdmin.getId())).isEmpty();
        assertThat(userRepository.findById(builtInAdmin.getId())).isPresent();
    }

    @Test
    void currentAccountCannotDeleteItselfOrChangeItsIdentity() {
        User secondAdmin = persist("second-admin", Role.ADMIN);
        userSession.setCurrentUser(secondAdmin);

        assertThatThrownBy(() -> userService.deleteUser(secondAdmin.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> userService.saveUser(
                request(secondAdmin.getId(), "renamed", Role.ADMIN), "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitivelyOnCreateAndUpdate() {
        User secretary = persist("secretary", Role.SECRETARY);

        assertThatThrownBy(() -> userService.saveUser(
                request(null, "ADMIN", Role.SECRETARY),
                "Strong-Pass-2026", "Strong-Pass-2026"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> userService.saveUser(
                request(secretary.getId(), "ADMIN", Role.SECRETARY), "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void newAndChangedPasswordsFollowTheSameBcryptLimitsAsInitialSetup() {
        assertThatThrownBy(() -> userService.saveUser(
                request(null, "short-password", Role.SECRETARY), "short", "short"))
                .isInstanceOf(IllegalArgumentException.class);

        String tooLong = "س".repeat(37);
        assertThatThrownBy(() -> userService.saveUser(
                request(null, "long-password", Role.SECRETARY), tooLong, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToCreateAUserWhenPasswordConfirmationDiffers() {
        assertThatThrownBy(() -> userService.saveUser(
                request(null, "new-secretary", Role.SECRETARY),
                "Strong-Pass-2026", "mistyped-pass"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(userRepository.findByUsername("new-secretary")).isEmpty();
    }

    private User persist(String username, Role role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .password(passwordEncoder.encode("Existing-Pass-2026"))
                .role(role)
                .build());
    }

    private User request(Long id, String username, Role role) {
        return User.builder().id(id).username(username).role(role).build();
    }
}
