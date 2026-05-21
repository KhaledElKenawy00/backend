package com.virtualoffice.notifications.template;

import com.virtualoffice.notifications.messaging.NotificationType;
import lombok.Getter;

@Getter
public enum EmailTemplate {

    SIGNUP_SUCCESS        ("signup-success.html",         "Welcome to Virtual Office"),
    LOGIN_SUCCESS         ("login-success.html",          "New sign-in to your Virtual Office account"),
    OTP                   ("otp.html",                    "Your Virtual Office verification code"),
    PASSWORD_RESET_SUCCESS("password-reset-success.html", "Your Virtual Office password was changed");

    private final String templateName;
    private final String templateSubject;

    EmailTemplate(String templateName, String templateSubject) {
        this.templateName = templateName;
        this.templateSubject = templateSubject;
    }

    public static EmailTemplate fromType(NotificationType type) {
        return switch (type) {
            case SIGNUP_SUCCESS         -> SIGNUP_SUCCESS;
            case LOGIN_SUCCESS          -> LOGIN_SUCCESS;
            case OTP                    -> OTP;
            case PASSWORD_RESET_SUCCESS -> PASSWORD_RESET_SUCCESS;
            case TASK_ASSIGNED          -> throw new IllegalArgumentException(
                    "TASK_ASSIGNED is not an email type");
        };
    }
}
