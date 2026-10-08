package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operations console. Requires ROLE_ADMIN.
 * TODO: admin accounts + sign-in (e.g. an admins table extending AccountAuthService, or SSO).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    public record ReviewRequest(@NotBlank @Size(max = 250) String reason) {
    }

    private final AdminService adminService;

    @GetMapping("/agents")
    public PageResponse<AgentProfileResponse> agents(@RequestParam(defaultValue = "UNDER_REVIEW") VerificationStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return adminService.agentsByStatus(status, page, size);
    }

    @PostMapping("/agents/{agentId}/approve")
    public AgentProfileResponse approveAgent(@PathVariable UUID agentId) {
        return adminService.approveAgent(agentId);
    }

    @PostMapping("/agents/{agentId}/reject")
    public AgentProfileResponse rejectAgent(@PathVariable UUID agentId, @Valid @RequestBody ReviewRequest request) {
        return adminService.rejectAgent(agentId, request.reason());
    }

    @PostMapping("/agents/{agentId}/documents/{type}/reject")
    public AgentProfileResponse rejectDocument(@PathVariable UUID agentId, @PathVariable DocumentType type,
                                               @Valid @RequestBody ReviewRequest request) {
        return adminService.rejectDocument(agentId, type, request.reason());
    }

    @PostMapping("/merchants/{merchantId}/approve")
    public void approveMerchant(@PathVariable UUID merchantId) {
        adminService.approveMerchant(merchantId);
    }
}
