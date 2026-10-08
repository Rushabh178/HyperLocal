package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.notification.entity.DeviceToken;
import com.HyperLocalSystem.app.security.CurrentUser;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Push-token registration for the three apps. Each path sits in its app's URL space, so the
 * security rules already guarantee the role; it is read from the token.
 */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    public record RegisterDeviceRequest(@NotBlank @Size(max = 512) String token, @NotNull DeviceToken.Platform platform) {
    }

    private final DeviceService deviceService;

    /** Call after every sign-in and whenever the push SDK hands out a new token. */
    @PostMapping({"/api/v1/customer/devices", "/api/v1/agent/devices", "/api/v1/merchant/devices"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDeviceRequest request) {
        Role role = Role.valueOf(jwt.getClaimAsString(TokenService.ROLE_CLAIM));
        deviceService.register(CurrentUser.id(jwt), role, request.token(), request.platform());
    }
}
