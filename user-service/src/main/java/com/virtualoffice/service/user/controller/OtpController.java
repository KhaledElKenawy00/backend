package com.virtualoffice.service.user.controller;

import com.virtualoffice.service.user.domain.enumuration.VerificationRequestType;
import com.virtualoffice.service.user.dto.ApiResponse;
import com.virtualoffice.service.user.dto.OtpRequest;
import com.virtualoffice.service.user.dto.VerifyOtpRequest;
import com.virtualoffice.service.user.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse> requestOtp(@RequestBody OtpRequest request) {
        ApiResponse result = otpService.requestOtp(request.getEmail(), VerificationRequestType.PASSWORD_RESET);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        ApiResponse result = otpService.verifyOtp(
                request.getEmail(),
                request.getOtp(),
                VerificationRequestType.PASSWORD_RESET
        );
        return ResponseEntity.ok(result);
    }
}