package com.HyperLocalSystem.app.agent.controller;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentUploadUrlRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentUploadUrlResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentsResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DutyRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DutyResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.LocationUpdateRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.UpdateAgentProfileRequest;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.service.AgentAuthService;
import com.HyperLocalSystem.app.agent.service.AgentProfileService;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ChangePasswordRequest;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/me")
@RequiredArgsConstructor
public class AgentProfileController {

    private final AgentProfileService profileService;
    private final AgentAuthService authService;

    @GetMapping
    public AgentProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(CurrentUser.id(jwt));
    }

    @PatchMapping
    public AgentProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateAgentProfileRequest request) {
        return profileService.update(CurrentUser.id(jwt), request);
    }

    @PutMapping("/password")
    public AuthResponse<AgentProfileResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(CurrentUser.id(jwt), request);
    }

    // ---- KYC documents ----

    @GetMapping("/documents")
    public DocumentsResponse documents(@AuthenticationPrincipal Jwt jwt) {
        return profileService.listDocuments(CurrentUser.id(jwt));
    }

    @PostMapping("/documents/{type}/upload-url")
    public DocumentUploadUrlResponse documentUploadUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable DocumentType type,
                                                       @Valid @RequestBody DocumentUploadUrlRequest request) {
        return profileService.createDocumentUploadUrl(CurrentUser.id(jwt), type, request);
    }

    @PutMapping("/documents/{type}")
    public DocumentsResponse saveDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable DocumentType type,
                                          @Valid @RequestBody DocumentRequest request) {
        return profileService.upsertDocument(CurrentUser.id(jwt), type, request);
    }

    // ---- duty & live location ----

    @PutMapping("/duty")
    public DutyResponse setDuty(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DutyRequest request) {
        return profileService.setDuty(CurrentUser.id(jwt), request.online());
    }

    /** REST fallback for the live location; the app should prefer the WebSocket stream (/app/agent/location). */
    @PostMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationUpdateRequest request) {
        profileService.updateLocation(CurrentUser.id(jwt), request);
    }
}
