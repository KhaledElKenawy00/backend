package com.virtualoffice.service.user.scheduler;

import com.virtualoffice.service.user.repository.VerificationRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationRequestScheduler {

    private final VerificationRequestRepository verificationRequestRepository;

    // Remove each expired record each hour
    @Scheduled(fixedRate = 60 * 60 * 1000) // takes milliseconds
    public void deleteExpiredRequests() {
        verificationRequestRepository.deleteAllExpired(LocalDateTime.now());
        log.info("Expired verification requests cleaned up at {}", LocalDateTime.now());
    }
}