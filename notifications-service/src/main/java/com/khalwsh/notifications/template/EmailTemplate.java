package com.khalwsh.notifications.template;

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
}
