package com.khalwsh.chat_service.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// the JWT is validated by Nginx upstream, so the X-User-* headers can be trusted as-is
public class UserContext {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private static final String USER = "USER";
    private static final String ADMIN = "ADMIN";

    @Getter
    @AllArgsConstructor
    public static class UserInfo {
        private final Integer userId;
        private final String role;

        public boolean isAdmin() {
            return ADMIN.equalsIgnoreCase(role);
        }
        public boolean isUser() { return USER.equalsIgnoreCase(role);}
    }

    public static UserInfo fromRequest(HttpServletRequest request) {
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        String role = request.getHeader(HEADER_USER_ROLE);

        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing X-User-Id header");
        }

        Integer userId;
        try {
            userId = Integer.parseInt(userIdHeader);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id must be a valid integer");
        }

        if (role == null || (!ADMIN.equalsIgnoreCase(role) && !USER.equalsIgnoreCase(role))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Role must be USER or ADMIN");
        }

        return new UserInfo(userId, role);
    }
}
