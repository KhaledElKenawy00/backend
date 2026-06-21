package com.virtualoffice.service.user.domain.entity;

import com.virtualoffice.service.user.domain.enumuration.VerificationRequestStatus;
import com.virtualoffice.service.user.domain.enumuration.VerificationRequestType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "verification_requests",
        indexes = {
                @Index(name = "idx_user_type", columnList = "user_id,type"),
                @Index(name = "idx_otp_hash", columnList = "otp_hash")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor


public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "otp_hash", nullable = false)
    private String otp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VerificationRequestStatus status = VerificationRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private VerificationRequestType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}