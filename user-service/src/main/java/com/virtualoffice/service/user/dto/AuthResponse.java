package com.virtualoffice.service.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class AuthResponse {
    private String token;
    private String email;
    private String firstName;
    private String lastName;
    private String errorMessage;

    public static AuthResponse withError(String errorMessage) {
        AuthResponse authResponse = new AuthResponse();
        authResponse.setErrorMessage(errorMessage);
        return authResponse;
    }
}