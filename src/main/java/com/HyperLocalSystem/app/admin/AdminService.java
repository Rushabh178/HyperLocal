package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AdminService {

    public PageResponse<AgentProfileResponse> agentsByStatus(VerificationStatus status, int page, int size) {
        throw new NotImplementedException("List partner applications");
    }

    /** TODO: mark all documents VERIFIED, verification_status = APPROVED, assign thermal_box_id. */
    public AgentProfileResponse approveAgent(UUID agentId) {
        throw new NotImplementedException("Approve partner");
    }

    public AgentProfileResponse rejectAgent(UUID agentId, String reason) {
        throw new NotImplementedException("Reject partner");
    }

    /** TODO: document REJECTED with remarks; agent back to PENDING_DOCUMENTS so they can re-upload. */
    public AgentProfileResponse rejectDocument(UUID agentId, DocumentType type, String reason) {
        throw new NotImplementedException("Reject partner document");
    }

    public void approveMerchant(UUID merchantId) {
        throw new NotImplementedException("Approve merchant");
    }
}
