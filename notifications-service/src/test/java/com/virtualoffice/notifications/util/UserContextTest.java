package com.virtualoffice.notifications.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    private final UserContext userContext = new UserContext();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bind(HttpServletRequest req) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void readsValidUserId() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "42");
        bind(req);

        assertThat(userContext.currentUserId()).isEqualTo(42L);
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "  42  ");
        bind(req);

        assertThat(userContext.currentUserId()).isEqualTo(42L);
    }

    @Test
    void acceptsNegativeUserId() {
        // We don't enforce userId>0 here; that's a producer-side invariant.
        // We just promise that the parsed Long is what reached us.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "-1");
        bind(req);

        assertThat(userContext.currentUserId()).isEqualTo(-1L);
    }

    @Test
    void acceptsLargeLongUserId() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "9999999999999");
        bind(req);

        assertThat(userContext.currentUserId()).isEqualTo(9999999999999L);
    }

    @Test
    void missingHeaderThrows401() {
        bind(new MockHttpServletRequest());

        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void blankHeaderThrows401() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "   ");
        bind(req);

        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonNumericHeaderThrows400() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "abc");
        bind(req);

        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void overflowingNumberThrows400() {
        // Long.MAX_VALUE + 1 — would overflow.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "99999999999999999999");
        bind(req);

        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void floatingPointHeaderThrows400() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "42.5");
        bind(req);

        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void noBoundRequestThrowsIllegalState() {
        // No bind() called.
        assertThatThrownBy(userContext::currentUserId)
                .isInstanceOf(IllegalStateException.class);
    }
}
