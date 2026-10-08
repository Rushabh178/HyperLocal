package com.HyperLocalSystem.app.customer.controller;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
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
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerSignupRequest;
import com.HyperLocalSystem.app.customer.service.CustomerAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer app sign-up / sign-in. Public endpoints.
 * <ol>
 *   <li>POST /signup → OTP sent</li>
 *   <li>POST /verify-phone with the OTP → tokens</li>
 *   <li>Later: POST /login (password) or /otp/send + /otp/login (password-less)</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody CustomerSignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/verify-phone")
    public AuthResponse<CustomerProfileResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return authService.verifyPhone(request);
    }

    @PostMapping("/verify-phone/resend")
    public OtpSentResponse resendVerificationOtp(@Valid @RequestBody PhoneRequest request) {
        return authService.resendVerificationOtp(request.phone());
    }

    @PostMapping("/login")
    public AuthResponse<CustomerProfileResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return authService.loginWithPassword(request);
    }

    @PostMapping("/otp/send")
    public OtpSentResponse sendLoginOtp(@Valid @RequestBody PhoneRequest request) {
        return authService.sendLoginOtp(request.phone());
    }

    @PostMapping("/otp/login")
    public AuthResponse<CustomerProfileResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        return authService.loginWithOtp(request);
    }

    @PostMapping("/refresh")
    public AuthTokens refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
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
