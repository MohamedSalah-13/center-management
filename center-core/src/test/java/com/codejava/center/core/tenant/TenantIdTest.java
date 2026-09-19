package com.codejava.center.core.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {

    @Test
    void desktopTenantIsStable() {
        assertThat(TenantId.DESKTOP.value()).isEqualTo(1L);
    }

    @Test
    void refusesNonPositiveIds() {
        assertThatThrownBy(() -> new TenantId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantId(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
