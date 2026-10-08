package com.HyperLocalSystem.app.customer.controller;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ChangePasswordRequest;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.UpdateCustomerProfileRequest;
import com.HyperLocalSystem.app.customer.service.CustomerAuthService;
import com.HyperLocalSystem.app.customer.service.CustomerProfileService;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/me")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService profileService;
    private final CustomerAuthService authService;

    @GetMapping
    public CustomerProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(CurrentUser.id(jwt));
    }

    @PatchMapping
    public CustomerProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return profileService.update(CurrentUser.id(jwt), request);
    }

    /** Signs out all other devices; the response carries a fresh session for this one. */
    @PutMapping("/password")
    public AuthResponse<CustomerProfileResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(CurrentUser.id(jwt), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        profileService.delete(CurrentUser.id(jwt));
    }
}
