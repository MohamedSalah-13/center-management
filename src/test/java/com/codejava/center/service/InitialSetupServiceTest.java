package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({InitialSetupService.class, AuditService.class, UserSession.class, SecurityConfig.class})
class InitialSetupServiceTest {

    @Autowired private InitialSetupService initialSetupService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void emptyUserTableRequiresSetup() {
        assertThat(initialSetupService.isSetupRequired()).isTrue();
    }

    @Test
    void createsOneEncodedAdministratorAndAuditsItAsSystem() {
        User admin = initialSetupService.createInitialAdmin("Strong-Pass-2026", "Strong-Pass-2026");

        assertThat(admin.getUsername()).isEqualTo(InitialSetupService.INITIAL_ADMIN_USERNAME);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPassword()).isNotEqualTo("Strong-Pass-2026");
        assertThat(passwordEncoder.matches("Strong-Pass-2026", admin.getPassword())).isTrue();
        assertThat(initialSetupService.isSetupRequired()).isFalse();

        Integer auditRows = jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where action = 'USER_CREATED'
                  and actor_username is null
                  and entity_label = 'admin'
                  and successful = true
                """, Integer.class);
        assertThat(auditRows).isEqualTo(1);
    }

    @Test
    void refusesASecondSetupAfterAnyUserExists() {
        userRepository.saveAndFlush(User.builder()
                .username("existing-user")
                .password("encoded")
                .role(Role.SECRETARY)
                .build());

        assertThatThrownBy(() -> initialSetupService.createInitialAdmin(
                "Strong-Pass-2026", "Strong-Pass-2026"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(userRepository.findByUsername("admin")).isEmpty();
    }

    @Test
    void rejectsShortOrMismatchedPasswordsWithoutCreatingAUser() {
        assertThatThrownBy(() -> initialSetupService.createInitialAdmin("short", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> initialSetupService.createInitialAdmin(
                "Strong-Pass-2026", "Different-Pass-2026"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsPasswordsBeyondBcryptsUtf8Limit() {
        String tooLong = "س".repeat(37); // 74 UTF-8 bytes

        assertThatThrownBy(() -> initialSetupService.createInitialAdmin(tooLong, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(userRepository.count()).isZero();
    }
}
