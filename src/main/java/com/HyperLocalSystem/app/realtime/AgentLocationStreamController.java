package com.HyperLocalSystem.app.realtime;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.LocationUpdateRequest;
import com.HyperLocalSystem.app.agent.service.AgentProfileService;
import com.HyperLocalSystem.app.common.exception.ApiError;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AgentLocationStreamController {

    private static final String AGENT_AUTHORITY = "ROLE_" + Role.DELIVERY_AGENT.name();

    private final AgentProfileService agentProfileService;

    /** Rider app: SEND /app/agent/location {"location":{"lat":..,"lng":..},"headingDegrees":..}. */
    @MessageMapping("/agent/location")
    public void onLocation(@Valid @Payload LocationUpdateRequest request, Principal principal) {
        Authentication auth = (Authentication) principal;
        boolean isAgent = auth.getAuthorities().stream().anyMatch(a -> AGENT_AUTHORITY.equals(a.getAuthority()));
        if (!isAgent) {
            throw new AccessDeniedException("Only delivery partners can stream location");
        }
        agentProfileService.updateLocation(UUID.fromString(auth.getName()), request);
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ApiError onApiError(ApiException ex) {
        return ApiError.of(ex.getStatus().value(), ex.getCode(), ex.getMessage(), "/app/agent/location");
    }
}
