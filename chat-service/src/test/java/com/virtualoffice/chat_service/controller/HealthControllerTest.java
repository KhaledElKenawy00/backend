package com.virtualoffice.chat_service.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void shouldReturnOK() {
        String result = controller.health();
        assertThat(result).isEqualTo("OK");
    }
}
