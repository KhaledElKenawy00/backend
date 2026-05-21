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
