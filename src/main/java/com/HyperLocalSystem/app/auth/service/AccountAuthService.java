package com.HyperLocalSystem.app.auth.service;

import com.HyperLocalSystem.app.auth.AuthAccount;
import com.HyperLocalSystem.app.auth.dto.AuthTokens;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ChangePasswordRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.MessageResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.OtpSentResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.PasswordLoginRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ResetPasswordRequest;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.VerifyPhoneRequest;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in flow shared by every account type. Each app (customer, delivery partner, merchant)
 * extends this with its own repository, role and sign-up fields, so the flows stay identical
 * while the accounts, tokens and URLs stay separate.
 *
 * @param <A> the account entity
 * @param <P> the profile DTO returned to that app
 */
public abstract class AccountAuthService<A extends AuthAccount, P> {

    protected final OtpService otpService;
    protected final TokenService tokenService;
    protected final PasswordEncoder passwordEncoder;

    protected AccountAuthService(OtpService otpService, TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    protected abstract Role role();

    protected abstract Optional<A> findByPhone(String normalizedPhone);

    protected abstract Optional<A> findByEmail(String normalizedEmail);

    protected abstract Optional<A> findById(UUID id);

    protected abstract P toProfile(A account);

    // ----------------------------------------------------------------- phone verification

    @Transactional
    public AuthResponse<P> verifyPhone(VerifyPhoneRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        A account = findActive(phone).orElseThrow(() -> ApiException.notFound("Account"));
        if (account.isPhoneVerified()) {
            throw ApiException.conflict("ALREADY_VERIFIED", "Phone number is already verified. Please sign in.");
        }
        otpService.verify(phone, role(), OtpPurpose.SIGNUP_VERIFY, request.otp());
        account.setPhoneVerified(true);
        return signIn(account);
    }

    @Transactional
    public OtpSentResponse resendVerificationOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        A account = findActive(phone).orElseThrow(() -> ApiException.notFound("Account"));
        if (account.isPhoneVerified()) {
            throw ApiException.conflict("ALREADY_VERIFIED", "Phone number is already verified. Please sign in.");
        }
        return new OtpSentResponse(phone, otpService.issue(phone, role(), OtpPurpose.SIGNUP_VERIFY));
    }

    // ----------------------------------------------------------------- sign in

    @Transactional
    public AuthResponse<P> loginWithPassword(PasswordLoginRequest request) {
        A account = findByIdentifier(request.identifier())
                .filter(candidate -> candidate.getAccountStatus() != AccountStatus.DELETED)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Phone/email or password is incorrect"));
        ensureCanSignIn(account);
        return signIn(account);
    }

    @Transactional
    public OtpSentResponse sendLoginOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        A account = findActive(phone)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND", "No account with this phone number. Please sign up."));
        ensureCanSignIn(account);
        return new OtpSentResponse(phone, otpService.issue(phone, role(), OtpPurpose.LOGIN));
    }

    @Transactional
    public AuthResponse<P> loginWithOtp(OtpLoginRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        A account = findActive(phone).orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Invalid or expired OTP"));
        otpService.verify(phone, role(), OtpPurpose.LOGIN, request.otp());
        ensureCanSignIn(account);
        return signIn(account);
    }

    // ----------------------------------------------------------------- tokens

    /** Not transactional on purpose: TokenService.rotate must commit its revocations even when it throws. */
    public AuthTokens refresh(String refreshToken) {
        return tokenService.rotate(refreshToken, role(), userId -> ensureCanSignIn(
                findById(userId).orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Please sign in again."))));
    }

    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken, role());
    }

    // ----------------------------------------------------------------- passwords

    /** Always answers the same way so the endpoint cannot be used to discover registered numbers. */
    @Transactional
    public MessageResponse forgotPassword(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        findActive(phone)
                .filter(AuthAccount::isPhoneVerified)
                .ifPresent(account -> otpService.issue(phone, role(), OtpPurpose.PASSWORD_RESET));
        return new MessageResponse("If an account exists for this number, an OTP has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        A account = findActive(phone).orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Invalid or expired OTP"));
        otpService.verify(phone, role(), OtpPurpose.PASSWORD_RESET, request.otp());
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        tokenService.revokeAll(account.getId(), role());
        return new MessageResponse("Password updated. Please sign in with your new password.");
    }

    /** Signs out every other device and returns a fresh session for this one. */
    @Transactional
    public AuthResponse<P> changePassword(UUID accountId, ChangePasswordRequest request) {
        A account = findById(accountId).orElseThrow(() -> ApiException.notFound("Account"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        tokenService.revokeAll(account.getId(), role());
        return signIn(account);
    }

    // ----------------------------------------------------------------- helpers

    protected AuthResponse<P> signIn(A account) {
        account.setLastLoginAt(Instant.now());
        return AuthResponse.of(tokenService.issue(account.getId(), role()), role(), toProfile(account));
    }

    protected void ensureCanSignIn(A account) {
        switch (account.getAccountStatus()) {
            case BLOCKED -> throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account has been blocked. Please contact support.");
            case DELETED -> throw ApiException.unauthorized("ACCOUNT_DELETED", "This account no longer exists");
            case ACTIVE -> {
            }
        }
        if (!account.isPhoneVerified()) {
            throw ApiException.forbidden("PHONE_NOT_VERIFIED", "Please verify your phone number to continue");
        }
    }

    protected Optional<A> findActive(String normalizedPhone) {
        return findByPhone(normalizedPhone).filter(account -> account.getAccountStatus() != AccountStatus.DELETED);
    }

    private Optional<A> findByIdentifier(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return findByEmail(normalizeEmail(trimmed));
        }
        if (!trimmed.matches(PhoneNumbers.INDIAN_MOBILE_REGEX)) {
            return Optional.empty();
        }
        return findByPhone(PhoneNumbers.normalize(trimmed));
    }

    protected static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
