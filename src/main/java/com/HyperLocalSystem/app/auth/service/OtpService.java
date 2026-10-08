package com.HyperLocalSystem.app.auth.service;

import com.HyperLocalSystem.app.auth.entity.OtpCode;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.auth.repository.OtpCodeRepository;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.security.Hashing;
import com.HyperLocalSystem.app.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpCodeRepository otpCodes;
    private final OtpSender otpSender;
    private final AppProperties properties;

    /** Generates and sends a fresh OTP, invalidating the previous one. Returns its expiry time. */
    @Transactional
    public Instant issue(String phone, Role role, OtpPurpose purpose) {
        AppProperties.Otp config = properties.otp();
        Instant now = Instant.now();

        otpCodes.findFirstByPhoneAndRoleAndPurposeOrderByCreatedAtDesc(phone, role, purpose).ifPresent(previous -> {
            if (previous.getCreatedAt().plus(config.resendCooldown()).isAfter(now)) {
                throw ApiException.tooManyRequests("OTP_COOLDOWN", "Please wait a few seconds before requesting another OTP");
            }
            previous.setConsumed(true);
        });

        String code = Hashing.randomDigits(config.length());
        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setRole(role);
        otp.setPurpose(purpose);
        otp.setCodeHash(hash(phone, role, purpose, code));
        otp.setExpiresAt(now.plus(config.ttl()));
        otpCodes.save(otp);

        otpSender.send(phone, code, purpose);
        return otp.getExpiresAt();
    }

    /**
     * Verifies and consumes the latest OTP. Runs in its own transaction so a failed attempt is
     * counted even though the caller's transaction rolls back on the thrown error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(String phone, Role role, OtpPurpose purpose, String code) {
        OtpCode otp = otpCodes.findFirstByPhoneAndRoleAndPurposeOrderByCreatedAtDesc(phone, role, purpose)
                .filter(candidate -> !candidate.isConsumed())
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Invalid or expired OTP"));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("OTP_EXPIRED", "OTP has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= properties.otp().maxAttempts()) {
            throw ApiException.tooManyRequests("OTP_ATTEMPTS_EXCEEDED", "Too many wrong attempts. Please request a new OTP.");
        }
        if (!Hashing.constantTimeEquals(otp.getCodeHash(), hash(phone, role, purpose, code))) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw ApiException.badRequest("OTP_INVALID", "Invalid or expired OTP");
        }
        otp.setConsumed(true);
    }

    private static String hash(String phone, Role role, OtpPurpose purpose, String code) {
        return Hashing.sha256Hex(phone + '|' + role + '|' + purpose + '|' + code);
    }
}
