package com.khalwsh.notifications.service;

import com.khalwsh.notifications.template.EmailTemplate;
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

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[^}]+}}");

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
