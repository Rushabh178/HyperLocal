package com.HyperLocalSystem.app.agent.controller;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentSignupRequest;
import com.HyperLocalSystem.app.agent.service.AgentAuthService;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.LogoutRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.MessageResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpSentResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PasswordLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PhoneRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.RefreshTokenRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ResetPasswordRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.VerifyPhoneRequest;
import com.HyperLocalSystem.app.auth.dto.AuthTokens;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery-partner app sign-up / sign-in. Public endpoints, separate from the customer ones.
 * <ol>
 *   <li>POST /signup (with vehicle details) → OTP sent</li>
 *   <li>POST /verify-phone → tokens; profile shows verificationStatus=PENDING_DOCUMENTS</li>
 *   <li>Upload KYC via /api/v1/agent/me/documents → UNDER_REVIEW → admin approves → APPROVED</li>
 *   <li>PUT /api/v1/agent/me/duty {online:true} to start receiving orders</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/agent/auth")
@RequiredArgsConstructor
public class AgentAuthController {

    private final AgentAuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody AgentSignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/verify-phone")
    public AuthResponse<AgentProfileResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return authService.verifyPhone(request);
    }

    @PostMapping("/verify-phone/resend")
    public OtpSentResponse resendVerificationOtp(@Valid @RequestBody PhoneRequest request) {
        return authService.resendVerificationOtp(request.phone());
    }

    @PostMapping("/login")
    public AuthResponse<AgentProfileResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return authService.loginWithPassword(request);
    }

    @PostMapping("/otp/send")
    public OtpSentResponse sendLoginOtp(@Valid @RequestBody PhoneRequest request) {
        return authService.sendLoginOtp(request.phone());
    }

    @PostMapping("/otp/login")
    public AuthResponse<AgentProfileResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        return authService.loginWithOtp(request);
    }

    @PostMapping("/refresh")
    public AuthTokens refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken(), request.deviceToken());
    }

    @PostMapping("/password/forgot")
    public MessageResponse forgotPassword(@Valid @RequestBody PhoneRequest request) {
        return authService.forgotPassword(request.phone());
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}
