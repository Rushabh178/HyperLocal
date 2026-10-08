package com.HyperLocalSystem.app.auth.dto;

import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request/response bodies shared by the customer, delivery-partner and merchant auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public static final String PHONE_MESSAGE = "must be a valid 10-digit Indian mobile number";
    public static final String OTP_REGEX = "^\\d{6}$";

    public record VerifyPhoneRequest(
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank @Pattern(regexp = OTP_REGEX, message = "must be 6 digits") String otp) {
    }

    /** {@code identifier} is either the phone number or the email address. */
    public record PasswordLoginRequest(
            @NotBlank String identifier,
            @NotBlank String password) {
    }

    public record PhoneRequest(
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = PHONE_MESSAGE) String phone) {
    }

    public record OtpLoginRequest(
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank @Pattern(regexp = OTP_REGEX, message = "must be 6 digits") String otp) {
    }

    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    /** {@code deviceToken}: this phone's push token, removed in the same call so it stops getting pushes. */
    public record LogoutRequest(@NotBlank String refreshToken, @Size(max = 512) String deviceToken) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank @Pattern(regexp = OTP_REGEX, message = "must be 6 digits") String otp,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /** Returned after sign-up: the account exists but the phone must be verified with the OTP. */
    public record SignupResponse(UUID userId, String phone, String nextStep, Instant otpExpiresAt) {
    }

    public record OtpSentResponse(String phone, Instant otpExpiresAt) {
    }

    public record MessageResponse(String message) {
    }

    /** Successful sign-in. {@code user} is the profile for the app that signed in. */
    public record AuthResponse<P>(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Role role,
            P user) {

        public static <P> AuthResponse<P> of(AuthTokens tokens, Role role, P user) {
            return new AuthResponse<>(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                    tokens.expiresIn(), role, user);
        }
    }
}
