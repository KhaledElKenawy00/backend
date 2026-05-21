package com.virtualoffice.notifications.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserContext {

    private static final String USER_ID_HEADER = "X-User-Id";

    public Long currentUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException("No active HTTP request bound to current thread");
        }

        HttpServletRequest request = attributes.getRequest();
        String headerValue = request.getHeader(USER_ID_HEADER);

        if (headerValue == null || headerValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing " + USER_ID_HEADER + " header");
        }

        try {
            return Long.parseLong(headerValue.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid " + USER_ID_HEADER + " header: not a number");
        }
    }
}
