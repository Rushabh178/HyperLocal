package com.HyperLocalSystem.app.merchant.controller;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpSentResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PasswordLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PhoneRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.RefreshTokenRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.VerifyPhoneRequest;
import com.HyperLocalSystem.app.auth.dto.AuthTokens;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantSignupRequest;
import com.HyperLocalSystem.app.merchant.service.MerchantAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kirana / restaurant app sign-in (third, separate auth space). */
@RestController
@RequestMapping("/api/v1/merchant/auth")
@RequiredArgsConstructor
public class MerchantAuthController {

    private final MerchantAuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody MerchantSignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/verify-phone")
    public AuthResponse<MerchantProfileResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return authService.verifyPhone(request);
    }

    @PostMapping("/login")
    public AuthResponse<MerchantProfileResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return authService.loginWithPassword(request);
    }

    @PostMapping("/otp/send")
    public OtpSentResponse sendLoginOtp(@Valid @RequestBody PhoneRequest request) {
        return authService.sendLoginOtp(request.phone());
    }

    @PostMapping("/otp/login")
    public AuthResponse<MerchantProfileResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
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
}
