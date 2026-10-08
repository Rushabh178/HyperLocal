package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.admin.dto.AdminDtos.ReviewRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentsResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Operations console. Requires ROLE_ADMIN (sign in at /api/v1/admin/auth/login). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ---- delivery partners ----

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

    // ---- stores ----

    @GetMapping("/merchants")
    public PageResponse<MerchantProfileResponse> merchants(@RequestParam(defaultValue = "PENDING") ApprovalStatus status,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return adminService.merchantsByStatus(status, page, size);
    }

    @GetMapping("/merchants/{merchantId}/documents")
    public MerchantDocumentsResponse merchantDocuments(@PathVariable UUID merchantId) {
        return adminService.merchantDocuments(merchantId);
    }

    @PostMapping("/merchants/{merchantId}/approve")
    public MerchantProfileResponse approveMerchant(@PathVariable UUID merchantId) {
        return adminService.approveMerchant(merchantId);
    }

    @PostMapping("/merchants/{merchantId}/reject")
    public MerchantProfileResponse rejectMerchant(@PathVariable UUID merchantId, @Valid @RequestBody ReviewRequest request) {
        return adminService.rejectMerchant(merchantId, request.reason());
    }
}
