package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.auth.service.AccountAuthService;
import com.HyperLocalSystem.app.auth.service.OtpService;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantSignupRequest;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.notification.DeviceService;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Kirana / restaurant sign-up and sign-in. Same flow as customers, but a separate account table,
 * OTPs scoped to MERCHANT and tokens carrying ROLE_MERCHANT. Merchants sign in with their phone
 * number (no email login). A new store starts PENDING and can't open until ops approves it.
 */
@Service
public class MerchantAuthService extends AccountAuthService<Merchant, MerchantProfileResponse> {

    private final MerchantRepository merchants;
    private final SpatialIndexService spatialIndex;

    public MerchantAuthService(MerchantRepository merchants, SpatialIndexService spatialIndex, OtpService otpService,
                               TokenService tokenService, PasswordEncoder passwordEncoder, DeviceService deviceService) {
        super(otpService, tokenService, passwordEncoder, deviceService);
        this.merchants = merchants;
        this.spatialIndex = spatialIndex;
    }

    /** Creates the store (unverified, PENDING approval) and sends an OTP. An unverified sign-up is reused. */
    @Transactional
    public SignupResponse signup(MerchantSignupRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        if (request.type() == MerchantType.RESTAURANT && isBlank(request.fssaiNumber())) {
            throw ApiException.badRequest("FSSAI_REQUIRED", "An FSSAI licence number is required for restaurants");
        }

        Merchant merchant = merchants.findByPhone(phone).orElseGet(Merchant::new);
        if (merchant.isPhoneVerified()) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered. Please sign in.");
        }
        if (merchant.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account cannot be registered. Please contact support.");
        }

        double lat = request.location().lat();
        double lng = request.location().lng();
        merchant.setType(request.type());
        merchant.setPhone(phone);
        merchant.setStoreName(request.storeName().trim());
        merchant.setOwnerName(request.ownerName().trim());
        merchant.setPasswordHash(passwordEncoder.encode(request.password()));
        merchant.setAddress(request.address().trim());
        merchant.setPincode(request.pincode());
        merchant.setLocation(GeoUtils.toPoint(lat, lng));
        merchant.setH3Index(spatialIndex.cellOf(lat, lng));
        merchant.setFssaiNumber(trimToNull(request.fssaiNumber()));
        merchant.setGstin(isBlank(request.gstin()) ? null : request.gstin().trim().toUpperCase(Locale.ROOT));
        merchant.setUpiVpa(trimToNull(request.upiVpa()));
        merchant.setCuisines(request.type() == MerchantType.RESTAURANT && request.cuisines() != null
                ? String.join(",", request.cuisines().stream().map(String::trim).toList()) : null);
        merchant.setImageUrl(trimToNull(request.imageUrl()));
        merchant.setApprovalStatus(ApprovalStatus.PENDING);
        merchant.setOpen(false);
        merchants.saveAndFlush(merchant);

        Instant otpExpiresAt = otpService.issue(phone, Role.MERCHANT, OtpPurpose.SIGNUP_VERIFY);
        return new SignupResponse(merchant.getId(), phone, "VERIFY_PHONE", otpExpiresAt);
    }

    @Override
    protected Role role() {
        return Role.MERCHANT;
    }

    @Override
    protected Optional<Merchant> findByPhone(String normalizedPhone) {
        return merchants.findByPhone(normalizedPhone);
    }

    @Override
    protected Optional<Merchant> findByEmail(String normalizedEmail) {
        return Optional.empty();
    }

    @Override
    protected Optional<Merchant> findById(UUID id) {
        return merchants.findById(id);
    }

    @Override
    protected MerchantProfileResponse toProfile(Merchant merchant) {
        return MerchantService.toResponse(merchant);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
