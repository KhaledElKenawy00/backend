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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateTest {

    @Test
    void hasExactlyFourValues() {
        assertThat(EmailTemplate.values()).hasSize(4);
    }

    @Test
    void everyTemplateFileExistsOnClasspath() {
        for (EmailTemplate template : EmailTemplate.values()) {
            ClassPathResource resource = new ClassPathResource(
                    "templates/email/" + template.getTemplateName());
            assertThat(resource.exists())
                    .as("Template file %s should exist", template.getTemplateName())
                    .isTrue();
        }
    }

    @Test
    void everyTemplateHasASubject() {
        for (EmailTemplate template : EmailTemplate.values()) {
            assertThat(template.getTemplateSubject())
                    .as("Subject for %s", template)
                    .isNotBlank();
        }
    }

    @Test
    void fromTypeMapsEachEmailType() {
        assertThat(EmailTemplate.fromType(NotificationType.SIGNUP_SUCCESS))
                .isEqualTo(EmailTemplate.SIGNUP_SUCCESS);
        assertThat(EmailTemplate.fromType(NotificationType.LOGIN_SUCCESS))
                .isEqualTo(EmailTemplate.LOGIN_SUCCESS);
        assertThat(EmailTemplate.fromType(NotificationType.OTP))
                .isEqualTo(EmailTemplate.OTP);
        assertThat(EmailTemplate.fromType(NotificationType.PASSWORD_RESET_SUCCESS))
                .isEqualTo(EmailTemplate.PASSWORD_RESET_SUCCESS);
    }

    @Test
    void fromTypeRejectsTaskAssigned() {
        assertThatThrownBy(() -> EmailTemplate.fromType(NotificationType.TASK_ASSIGNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TASK_ASSIGNED");
    }
}
