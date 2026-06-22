package com.virtualoffice.service.user.repository;

import com.virtualoffice.service.user.domain.entity.VerificationRequest;
import com.virtualoffice.service.user.domain.enumuration.VerificationRequestStatus;
import com.virtualoffice.service.user.domain.enumuration.VerificationRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    // Get the OTP for a user (by id), verification type and status
    Optional<VerificationRequest> getOtpByUser_IdAndTypeAndStatus(
            Long userId, VerificationRequestType type, VerificationRequestStatus status);

    // Delete all expired records (used by the scheduler)
    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationRequest v WHERE v.expiresAt < :now")
    void deleteAllExpired(@Param("now") LocalDateTime now);
}
