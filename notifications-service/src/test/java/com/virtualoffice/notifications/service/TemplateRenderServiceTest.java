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
package com.virtualoffice.notifications.service;

import com.virtualoffice.notifications.template.EmailTemplate;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRenderServiceTest {

    private final TemplateRenderService renderer = new TemplateRenderService();

    @Test
    void substitutesAllProvidedPlaceholders() {
        String result = renderer.render(EmailTemplate.OTP, Map.of(
                "firstName", "Khaled",
                "otp", "483920",
                "expiresInMinutes", "10"
        ));

        assertThat(result).contains("Khaled");
        assertThat(result).contains("483920");
        assertThat(result).contains("10");
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void missingPlaceholdersBlankOut() {
        String result = renderer.render(EmailTemplate.OTP, Map.of("firstName", "Khaled"));

        assertThat(result).contains("Khaled");
        assertThat(result).doesNotContain("{{otp}}");
        assertThat(result).doesNotContain("{{expiresInMinutes}}");
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void nullVarsBlanksAllPlaceholders() {
        String result = renderer.render(EmailTemplate.OTP, null);

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void emptyVarsBlanksAllPlaceholders() {
        String result = renderer.render(EmailTemplate.SIGNUP_SUCCESS, Map.of());

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void nullValueIsTreatedAsEmptyString() {
        Map<String, String> vars = new HashMap<>();
        vars.put("firstName", null);
        vars.put("email", "k@x.com");

        String result = renderer.render(EmailTemplate.SIGNUP_SUCCESS, vars);

        assertThat(result).contains("k@x.com");
        assertThat(result).doesNotContain("{{firstName}}");
        assertThat(result).doesNotContain("null");
    }

    @Test
    void unknownVarsAreSilentlyIgnored() {
        String result = renderer.render(EmailTemplate.SIGNUP_SUCCESS, Map.of(
                "firstName", "Khaled",
                "email", "k@x.com",
                "totallyUnused", "should-not-appear"
        ));

        assertThat(result).contains("Khaled");
        assertThat(result).doesNotContain("should-not-appear");
    }

    @Test
    void valuesContainingPlaceholderDelimitersDoNotBreak() {
        String result = renderer.render(EmailTemplate.OTP, Map.of(
                "firstName", "{{evil}}",
                "otp",       "code-123",
                "expiresInMinutes", "5"
        ));

        // The unresolved-placeholder sweep runs once after all substitutions,
        // so "{{evil}}" inside a value gets removed by the sweep. That's
        // acceptable; the alternative is XSS-style injection risk from payload.
        assertThat(result).doesNotContain("{{");
        assertThat(result).contains("code-123");
    }

    @Test
    void allFourTemplatesAreReadable() {
        for (EmailTemplate template : EmailTemplate.values()) {
            String result = renderer.render(template, Map.of());
            assertThat(result)
                    .as("Template %s should be readable from classpath", template)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    void substitutesValuesContainingSpecialRegexChars() {
        // The renderer uses String.replace, not replaceAll, so $ and \ in
        // values must not be interpreted as regex replacement metachars.
        String result = renderer.render(EmailTemplate.LOGIN_SUCCESS, Map.of(
                "firstName", "K$h\\al",
                "loginAt", "2026-05-16 14:00",
                "ip", "1.2.3.4",
                "userAgent", "agent"
        ));

        assertThat(result).contains("K$h\\al");
    }

    @Test
    void multilineValuesAreSubstitutedAsIs() {
        String result = renderer.render(EmailTemplate.LOGIN_SUCCESS, Map.of(
                "firstName", "Khaled",
                "loginAt", "2026-05-16\n14:00",
                "ip", "1.2.3.4",
                "userAgent", "agent"
        ));

        assertThat(result).contains("2026-05-16\n14:00");
    }
}
