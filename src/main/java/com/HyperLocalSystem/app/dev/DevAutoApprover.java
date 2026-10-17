package com.HyperLocalSystem.app.dev;

import com.HyperLocalSystem.app.admin.AdminService;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DEVELOPMENT ONLY ("dev" profile and app.dev.auto-approve=true): approves new stores and delivery
 * partners a few seconds after they sign up, so testing doesn't wait for an ops review. Partners are
 * approved even without KYC documents. Rejected accounts stay rejected.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev.auto-approve", havingValue = "true")
@RequiredArgsConstructor
public class DevAutoApprover {

    private static final PageRequest BATCH = PageRequest.of(0, 50);

    private final MerchantRepository merchants;
    private final DeliveryAgentRepository agents;
    private final AdminService adminService;

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void approvePending() {
        for (Merchant merchant : merchants.findByApprovalStatusOrderByCreatedAt(ApprovalStatus.PENDING, BATCH)) {
            adminService.approveMerchant(merchant.getId());
            log.warn("[DEV AUTO-APPROVE] store {} ({})", merchant.getStoreName(), merchant.getPhone());
        }
        for (VerificationStatus status : List.of(VerificationStatus.PENDING_DOCUMENTS, VerificationStatus.UNDER_REVIEW)) {
            for (DeliveryAgent agent : agents.findByVerificationStatusOrderByCreatedAt(status, BATCH)) {
                // approveAgent only accepts applications under review; skip the document step.
                agent.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
                adminService.approveAgent(agent.getId());
                log.warn("[DEV AUTO-APPROVE] delivery partner {} ({})", agent.getName(), agent.getPhone());
            }
        }
    }
}
