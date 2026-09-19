package com.codejava.center.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorIdentityTest {

    @Test
    void comparesRolesExactly() {
        ActorIdentity actor = new ActorIdentity(7L, "owner", "ADMIN");

        assertThat(actor.hasRole("ADMIN")).isTrue();
        assertThat(actor.hasRole("admin")).isFalse();
    }

    @Test
    void refusesBlankIdentityFields() {
        assertThatThrownBy(() -> new ActorIdentity(7L, " ", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActorIdentity(7L, "owner", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
