package com.HyperLocalSystem.app.customer.service;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.SignupResponse;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.auth.service.AccountAuthService;
import com.HyperLocalSystem.app.auth.service.OtpService;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerSignupRequest;
import com.HyperLocalSystem.app.customer.entity.Customer;
import com.HyperLocalSystem.app.customer.repository.CustomerRepository;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerAuthService extends AccountAuthService<Customer, CustomerProfileResponse> {

    private final CustomerRepository customers;

    public CustomerAuthService(CustomerRepository customers, OtpService otpService,
                               TokenService tokenService, PasswordEncoder passwordEncoder) {
        super(otpService, tokenService, passwordEncoder);
        this.customers = customers;
    }

    /**
     * Creates the account (unverified) and sends an OTP. If the number registered before but never
     * verified, that pending account is reused so the user can simply sign up again.
     */
    @Transactional
    public SignupResponse signup(CustomerSignupRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        String email = normalizeEmail(request.email());

        // Deleted accounts have their phone tombstoned, so a match here is a live (maybe unverified) account.
        Customer customer = customers.findByPhone(phone).orElseGet(Customer::new);
        if (customer.isPhoneVerified()) {
            throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered. Please sign in.");
        }
        if (customer.getAccountStatus() == AccountStatus.BLOCKED) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account has been blocked. Please contact support.");
        }
        if (email != null) {
            customers.findByEmailIgnoreCase(email)
                    .filter(other -> !other.getId().equals(customer.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already linked to another account");
                    });
        }

        customer.setPhone(phone);
        customer.setFullName(request.fullName().trim());
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customers.saveAndFlush(customer);

        Instant otpExpiresAt = otpService.issue(phone, Role.CUSTOMER, OtpPurpose.SIGNUP_VERIFY);
        return new SignupResponse(customer.getId(), phone, "VERIFY_PHONE", otpExpiresAt);
    }

    @Override
    protected Role role() {
        return Role.CUSTOMER;
    }

    @Override
    protected Optional<Customer> findByPhone(String normalizedPhone) {
        return customers.findByPhone(normalizedPhone);
    }

    @Override
    protected Optional<Customer> findByEmail(String normalizedEmail) {
        return customers.findByEmailIgnoreCase(normalizedEmail);
    }

    @Override
    protected Optional<Customer> findById(UUID id) {
        return customers.findById(id);
    }

    @Override
    protected CustomerProfileResponse toProfile(Customer customer) {
        return CustomerProfileService.toResponse(customer);
    }

    /** Unique placeholder (fits varchar(15)) that releases the real number of a deleted account. */
    static String tombstone(UUID id) {
        return "del-" + id.toString().replace("-", "").substring(0, 11);
    }
}
