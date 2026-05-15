package com.khalwsh.notifications.service;

import com.khalwsh.notifications.template.EmailTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class TemplateRenderService {

    public String render(EmailTemplate template, Map<String, String> vars) {
        if (vars == null) {
            vars = Map.of();
        }

        String path = "templates/email/" + template.getTemplateName();
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load email template: " + path, e);
        }
    }
}
