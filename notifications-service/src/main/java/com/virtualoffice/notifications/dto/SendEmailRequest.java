package com.virtualoffice.notifications.dto;

import com.virtualoffice.notifications.template.EmailTemplate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SendEmailRequest {

    @NotNull
    private EmailTemplate template;

    @Email
    @NotBlank
    private String to;

    private Map<String, String> vars;
}
