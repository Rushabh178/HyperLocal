package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.entity.AgentDocument;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.AgentDocumentRepository;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.agent.service.AgentProfileService;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentsResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.merchant.service.MerchantDocumentService;
import com.HyperLocalSystem.app.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Ops reviews: delivery-partner KYC and new stores. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final DeliveryAgentRepository agents;
    private final AgentDocumentRepository documents;
    private final AgentProfileService agentProfileService;
    private final MerchantRepository merchants;
    private final MerchantDocumentService merchantDocuments;

    // ----------------------------------------------------------------- delivery partners

    @Transactional(readOnly = true)
    public PageResponse<AgentProfileResponse> agentsByStatus(VerificationStatus status, int page, int size) {
        return PageResponse.of(agents.findByVerificationStatusOrderByCreatedAt(status, PageResponse.request(page, size)),
                agentProfileService::toResponse);
    }

    /** Every document VERIFIED, status APPROVED and a thermal box assigned; the partner can now go online. */
    @Transactional
    public AgentProfileResponse approveAgent(UUID agentId) {
        DeliveryAgent agent = loadAgent(agentId);
        if (agent.getVerificationStatus() != VerificationStatus.UNDER_REVIEW) {
            throw ApiException.conflict("NOT_UNDER_REVIEW", "Only applications under review can be approved");
        }
        documents.findByAgentIdOrderByType(agentId).forEach(doc -> {
            doc.setStatus(AgentDocument.Status.VERIFIED);
            doc.setRemarks(null);
        });
        agent.setVerificationStatus(VerificationStatus.APPROVED);
        agent.setRejectionReason(null);
        if (agent.getThermalBoxId() == null) {
            // TODO(edge-case): assign from the real thermal-box inventory instead of a generated serial.
            agent.setThermalBoxId("TB-" + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt()).toUpperCase());
        }
        return agentProfileService.toResponse(agent);
    }

    @Transactional
    public AgentProfileResponse rejectAgent(UUID agentId, String reason) {
        DeliveryAgent agent = loadAgent(agentId);
        agent.setVerificationStatus(VerificationStatus.REJECTED);
        agent.setRejectionReason(reason.trim());
        // TODO(edge-case): force an approved partner offline (and reassign their route) when rejecting later.
        return agentProfileService.toResponse(agent);
    }

    /** The document goes back to the partner with remarks; they re-upload and the application returns to review. */
    @Transactional
    public AgentProfileResponse rejectDocument(UUID agentId, DocumentType type, String reason) {
        DeliveryAgent agent = loadAgent(agentId);
        AgentDocument document = documents.findByAgentIdAndType(agentId, type)
                .orElseThrow(() -> ApiException.notFound("Document"));
        document.setStatus(AgentDocument.Status.REJECTED);
        document.setRemarks(reason.trim());
        agent.setVerificationStatus(VerificationStatus.PENDING_DOCUMENTS);
        documents.flush();
        return agentProfileService.toResponse(agent);
    }

    // ----------------------------------------------------------------- stores

    @Transactional(readOnly = true)
    public PageResponse<MerchantProfileResponse> merchantsByStatus(ApprovalStatus status, int page, int size) {
        return PageResponse.of(merchants.findByApprovalStatusOrderByCreatedAt(status, PageResponse.request(page, size)),
                MerchantService::toResponse);
    }

    /** The store's documents with their file links (open them with GET /files/{key} as admin). */
    @Transactional(readOnly = true)
    public MerchantDocumentsResponse merchantDocuments(UUID merchantId) {
        return merchantDocuments.toResponse(loadMerchant(merchantId));
    }

    @Transactional
    public MerchantProfileResponse approveMerchant(UUID merchantId) {
        Merchant merchant = loadMerchant(merchantId);
        merchantDocuments.verifyAll(merchantId);
        // TODO(edge-case): refuse approval while required documents are missing.
        merchant.setApprovalStatus(ApprovalStatus.APPROVED);
        merchant.setRejectionReason(null);
        return MerchantService.toResponse(merchant);
    }

    /** A rejected store is closed and disappears from the catalogue. */
    @Transactional
    public MerchantProfileResponse rejectMerchant(UUID merchantId, String reason) {
        Merchant merchant = loadMerchant(merchantId);
        merchant.setApprovalStatus(ApprovalStatus.REJECTED);
        merchant.setRejectionReason(reason.trim());
        merchant.setOpen(false);
        return MerchantService.toResponse(merchant);
    }

    private DeliveryAgent loadAgent(UUID agentId) {
        return agents.findById(agentId).orElseThrow(() -> ApiException.notFound("Delivery partner"));
    }

    private Merchant loadMerchant(UUID merchantId) {
        return merchants.findById(merchantId).orElseThrow(() -> ApiException.notFound("Merchant"));
    }
}
