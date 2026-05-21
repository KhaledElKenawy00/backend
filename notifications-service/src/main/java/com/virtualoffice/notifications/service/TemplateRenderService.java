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
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TemplateRenderService {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\{\{[^}]+}}");

    public String render(EmailTemplate template, Map<String, String> vars) {
        Map<String, String> safeVars = vars != null ? vars : Map.of();

        String path = "templates/email/" + template.getTemplateName();
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : safeVars.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue() : "";
                content = content.replace("{{" + entry.getKey() + "}}", value);
            }

            // Per arc section 7: missing placeholders blank out (never leave {{x}} visible).
            content = blankUnresolvedPlaceholders(content);

            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load email template: " + path, e);
        }
    }

    private String blankUnresolvedPlaceholders(String content) {
        Matcher matcher = UNRESOLVED_PLACEHOLDER.matcher(content);
        if (!matcher.find()) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            log.warn("Unresolved placeholder in email body: {}", matcher.group());
            matcher.appendReplacement(sb, "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
