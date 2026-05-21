/*
 * Copyright (c) 2025 My Virtual Office
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
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
