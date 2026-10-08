package com.HyperLocalSystem.app.agent.service;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentSignupRequest;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.DutyStatus;
import com.HyperLocalSystem.app.agent.entity.VehicleType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.auth.service.AccountAuthService;
import com.HyperLocalSystem.app.auth.service.OtpService;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Delivery-partner sign-up / sign-in. Same flow as customers, but a separate account table,
 * separate OTPs (scoped by role) and tokens carrying ROLE_DELIVERY_AGENT.
 * A partner can sign in right after verifying their phone, but cannot go on duty until KYC is APPROVED.
 */
@Service
public class AgentAuthService extends AccountAuthService<DeliveryAgent, AgentProfileResponse> {

    private static final String VEHICLE_NUMBER_REGEX = "^[A-Z0-9]{6,11}$";

    private final DeliveryAgentRepository agents;
    private final AgentProfileService profileService;

    public AgentAuthService(DeliveryAgentRepository agents, AgentProfileService profileService, OtpService otpService,
                            TokenService tokenService, PasswordEncoder passwordEncoder) {
        super(otpService, tokenService, passwordEncoder);
        this.agents = agents;
        this.profileService = profileService;
    }

    @Transactional
    public SignupResponse signup(AgentSignupRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        String email = normalizeEmail(request.email());
        String vehicleNumber = normalizeVehicleNumber(request.vehicleType(), request.vehicleNumber());

        DeliveryAgent agent = agents.findByPhone(phone).orElseGet(DeliveryAgent::new);
        if (agent.isPhoneVerified()) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered. Please sign in.");
        }
        if (agent.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account cannot be registered. Please contact support.");
        }
        if (email != null) {
            agents.findByEmailIgnoreCase(email)
                    .filter(other -> !other.getId().equals(agent.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already linked to another account");
                    });
        }

        agent.setPhone(phone);
        agent.setName(request.fullName().trim());
        agent.setEmail(email);
        agent.setPasswordHash(passwordEncoder.encode(request.password()));
        agent.setVehicleType(request.vehicleType());
        agent.setVehicleNumber(vehicleNumber);
        agent.setCity(request.city().trim());
        agent.setVerificationStatus(VerificationStatus.PENDING_DOCUMENTS);
        agent.setDutyStatus(DutyStatus.OFFLINE);
        agents.saveAndFlush(agent);

        Instant otpExpiresAt = otpService.issue(phone, Role.DELIVERY_AGENT, OtpPurpose.SIGNUP_VERIFY);
        return new SignupResponse(agent.getId(), phone, "VERIFY_PHONE", otpExpiresAt);
    }

    private static String normalizeVehicleNumber(VehicleType type, String raw) {
        if (type == VehicleType.BICYCLE) {
            return null;
        }
        String normalized = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches(VEHICLE_NUMBER_REGEX)) {
            throw ApiException.badRequest("INVALID_VEHICLE_NUMBER", "Enter the registration number shown on your RC, e.g. MH12AB1234");
        }
        return normalized;
    }

    @Override
    protected Role role() {
        return Role.DELIVERY_AGENT;
    }

    @Override
    protected Optional<DeliveryAgent> findByPhone(String normalizedPhone) {
        return agents.findByPhone(normalizedPhone);
    }

    @Override
    protected Optional<DeliveryAgent> findByEmail(String normalizedEmail) {
        return agents.findByEmailIgnoreCase(normalizedEmail);
    }

    @Override
    protected Optional<DeliveryAgent> findById(UUID id) {
        return agents.findById(id);
    }

    @Override
    protected AgentProfileResponse toProfile(DeliveryAgent agent) {
        return profileService.toResponse(agent);
    }
}
