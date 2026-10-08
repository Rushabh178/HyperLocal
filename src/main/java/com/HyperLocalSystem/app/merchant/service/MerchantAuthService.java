package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpSentResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PasswordLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.VerifyPhoneRequest;
import com.HyperLocalSystem.app.auth.dto.AuthTokens;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantSignupRequest;
import org.springframework.stereotype.Service;

/**
 * TODO: create a {@code Merchant} entity (merchants table: type, store_name, owner_name, phone,
 * password_hash, location geometry(Point,4326), h3_index, is_open, verification_status, upi_vpa, ...)
 * implementing AuthAccount, then make this class extend
 * {@code AccountAuthService<Merchant, MerchantProfileResponse>} exactly like CustomerAuthService.
 * That gives you verify-phone, login, OTP login, refresh, logout and password reset for free.
 */
@Service
public class MerchantAuthService {

    public SignupResponse signup(MerchantSignupRequest request) {
        throw new NotImplementedException("Merchant sign-up");
    }

    public AuthResponse<MerchantProfileResponse> verifyPhone(VerifyPhoneRequest request) {
        throw new NotImplementedException("Merchant phone verification");
    }

    public AuthResponse<MerchantProfileResponse> loginWithPassword(PasswordLoginRequest request) {
        throw new NotImplementedException("Merchant login");
    }

    public OtpSentResponse sendLoginOtp(String phone) {
        throw new NotImplementedException("Merchant OTP");
    }

    public AuthResponse<MerchantProfileResponse> loginWithOtp(OtpLoginRequest request) {
        throw new NotImplementedException("Merchant OTP login");
    }

    public AuthTokens refresh(String refreshToken) {
        throw new NotImplementedException("Merchant token refresh");
    }

    public void logout(String refreshToken) {
        throw new NotImplementedException("Merchant logout");
    }
}
