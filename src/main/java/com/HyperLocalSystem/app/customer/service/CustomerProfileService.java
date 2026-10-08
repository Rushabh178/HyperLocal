package com.HyperLocalSystem.app.customer.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.UpdateCustomerProfileRequest;
import com.HyperLocalSystem.app.customer.entity.Customer;
import com.HyperLocalSystem.app.customer.repository.CustomerRepository;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerRepository customers;
    private final UnifiedOrderRepository orders;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public CustomerProfileResponse get(UUID customerId) {
        return toResponse(load(customerId));
    }

    @Transactional
    public CustomerProfileResponse update(UUID customerId, UpdateCustomerProfileRequest request) {
        Customer customer = load(customerId);
        if (request.fullName() != null) {
            customer.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            String email = request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
            if (email != null) {
                customers.findByEmailIgnoreCase(email)
                        .filter(other -> !other.getId().equals(customerId))
                        .ifPresent(other -> {
                            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already linked to another account");
                        });
            }
            customer.setEmail(email);
        }
        return toResponse(customer);
    }

    /**
     * Soft delete; frees the phone number for a future sign-up and signs out every device.
     * Refused while an order is still in flight.
     */
    @Transactional
    public void delete(UUID customerId) {
        Customer customer = load(customerId);
        if (orders.existsByCustomerIdAndStatusIn(customerId, OrderStatus.ACTIVE)) {
            throw ApiException.conflict("ORDER_IN_PROGRESS", "You can delete your account once your current order is delivered or cancelled");
        }
        customer.setAccountStatus(AccountStatus.DELETED);
        customer.setPhone(CustomerAuthService.tombstone(customerId));
        customer.setEmail(null);
        tokenService.revokeAll(customerId, Role.CUSTOMER);
        // TODO(edge-case): anonymise addresses per the data-retention policy.
    }

    private Customer load(UUID customerId) {
        return customers.findById(customerId)
                .filter(customer -> customer.getAccountStatus() != AccountStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    static CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), customer.isPhoneVerified(), customer.getCreatedAt());
    }
}
