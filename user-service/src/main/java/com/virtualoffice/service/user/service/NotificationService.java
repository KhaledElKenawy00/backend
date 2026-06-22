package com.virtualoffice.service.user.service;

import com.virtualoffice.service.user.domain.entity.User;
import com.virtualoffice.service.user.domain.enumuration.NotificationType;
import com.virtualoffice.service.user.notifications.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationPublisher notificationPublisher;

    public void registerNotification(User user) {
        notificationPublisher.publish(NotificationType.SIGNUP_SUCCESS, Map.of(
                "email", user.getEmail(),
                "firstName", user.getFirstName()
        ));
    }

    public void otpNotification(User user, String otp) {
        notificationPublisher.publish(NotificationType.OTP, Map.of(
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "otp", otp,
                "expiresInMinutes", 10
        ));
    }

    public void passwordResetNotification(User user) {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String resetAt = LocalDateTime.now().format(formatter);
        notificationPublisher.publish(NotificationType.PASSWORD_RESET_SUCCESS, Map.of(
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "resetAt", resetAt
        ));
    }
}
