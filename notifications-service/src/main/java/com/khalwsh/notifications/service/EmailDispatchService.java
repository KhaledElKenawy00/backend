package com.khalwsh.notifications.service;

import com.khalwsh.notifications.template.EmailTemplate;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailDispatchService {

    private final JavaMailSender mailSender;
    private final TemplateRenderService templateRenderer;

    public void dispatch(EmailTemplate template, String to, Map<String, String> vars) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    "UTF-8"
            );

            helper.setTo(to);
            helper.setSubject(template.getTemplateSubject());

            String html = templateRenderer.render(template, vars);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
