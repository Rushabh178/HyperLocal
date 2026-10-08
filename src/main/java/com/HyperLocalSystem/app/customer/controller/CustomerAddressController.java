package com.HyperLocalSystem.app.customer.controller;

import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.AddressRequest;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.AddressResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.ServiceabilityResponse;
import com.HyperLocalSystem.app.customer.service.CustomerAddressService;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/addresses")
@RequiredArgsConstructor
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    /** Default address first, then most recently updated. */
    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return addressService.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return addressService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{addressId}")
    public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        return addressService.get(CurrentUser.id(jwt), addressId);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(CurrentUser.id(jwt), addressId, request);
    }

    @PutMapping("/{addressId}/default")
    public AddressResponse setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        return addressService.setDefault(CurrentUser.id(jwt), addressId);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        addressService.delete(CurrentUser.id(jwt), addressId);
    }

    /** Call before saving a pin: is it inside the 2 km operating radius? */
    @PostMapping("/serviceability")
    public ServiceabilityResponse checkServiceability(@Valid @RequestBody GeoPoint location) {
        return addressService.checkServiceability(location);
    }
}
