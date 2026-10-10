package com.HyperLocalSystem.app.customer.dto;

import com.HyperLocalSystem.app.auth.dto.AuthDtos;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.customer.entity.AddressLabel;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    // ----------------------------------------------------------------- sign up / profile

    public record CustomerSignupRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** Partial update: null fields are left unchanged. */
    public record UpdateCustomerProfileRequest(
            @Size(min = 1, max = 100) String fullName,
            @Email @Size(max = 150) String email) {
    }

    public record CustomerProfileResponse(
            UUID id,
            String fullName,
            String phone,
            String email,
            boolean phoneVerified,
            Instant createdAt) {
    }

    // ----------------------------------------------------------------- addresses

    public record AddressRequest(
            @NotNull AddressLabel label,
            @Size(max = 30) String customLabel,
            @Size(max = 100) String recipientName,
            @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = AuthDtos.PHONE_MESSAGE) String recipientPhone,
            @NotBlank @Size(max = 50) String houseNumber,
            @Size(max = 100) String buildingName,
            @Size(max = 150) String street,
            @Size(max = 150) String landmark,
            @Size(max = 100) String area,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Size(max = 60) String state,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$", message = "must be a 6-digit PIN code") String pincode,
            @NotNull @Valid GeoPoint location,
            @Size(max = 250) String deliveryInstructions,
            Boolean makeDefault) {
    }

    public record AddressResponse(
            UUID id,
            AddressLabel label,
            String customLabel,
            String recipientName,
            String recipientPhone,
            String houseNumber,
            String buildingName,
            String street,
            String landmark,
            String area,
            String city,
            String state,
            String pincode,
            String fullAddress,
            GeoPoint location,
            String h3Index,
            String deliveryInstructions,
            boolean isDefault) {
    }

    public record ServiceabilityResponse(
            boolean serviceable,
            /** The widest radius (restaurants'); kept for older apps. */
            int serviceRadiusMeters,
            int nearbyRestaurants,
            int nearbyKiranaStores,
            String message,
            int restaurantRadiusMeters,
            int kiranaRadiusMeters) {
    }
}
