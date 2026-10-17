package com.HyperLocalSystem.app.dev;

import com.HyperLocalSystem.app.admin.AdminService;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Auto-approval exists only with "dev" AND the switch on, and never touches rejected accounts. */
class DevAutoApproverTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevAutoApprover.class)
            .withBean(MerchantRepository.class, () -> mock(MerchantRepository.class))
            .withBean(DeliveryAgentRepository.class, () -> mock(DeliveryAgentRepository.class))
            .withBean(AdminService.class, () -> mock(AdminService.class));

    @Test
    void onlyDevWithTheSwitchOnCreatesTheApprover() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.dev.auto-approve=true")
                .run(context -> assertThat(context).hasSingleBean(DevAutoApprover.class));
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(DevAutoApprover.class));
        runner.withPropertyValues("spring.profiles.active=prod", "app.dev.auto-approve=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevAutoApprover.class));
        runner.withPropertyValues("app.dev.auto-approve=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevAutoApprover.class));
    }

    @Test
    void approvesPendingStoresAndPartnersOnly() {
        MerchantRepository merchants = mock(MerchantRepository.class);
        DeliveryAgentRepository agents = mock(DeliveryAgentRepository.class);
        AdminService admin = mock(AdminService.class);

        Merchant store = new Merchant();
        store.setId(UUID.randomUUID());
        DeliveryAgent noDocuments = agent(VerificationStatus.PENDING_DOCUMENTS);
        DeliveryAgent underReview = agent(VerificationStatus.UNDER_REVIEW);
        when(merchants.findByApprovalStatusOrderByCreatedAt(eq(ApprovalStatus.PENDING), any())).thenReturn(page(store));
        when(agents.findByVerificationStatusOrderByCreatedAt(eq(VerificationStatus.PENDING_DOCUMENTS), any())).thenReturn(page(noDocuments));
        when(agents.findByVerificationStatusOrderByCreatedAt(eq(VerificationStatus.UNDER_REVIEW), any())).thenReturn(page(underReview));

        new DevAutoApprover(merchants, agents, admin).approvePending();

        verify(admin).approveMerchant(store.getId());
        verify(admin).approveAgent(noDocuments.getId());
        verify(admin).approveAgent(underReview.getId());
        assertThat(noDocuments.getVerificationStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        verify(merchants, never()).findByApprovalStatusOrderByCreatedAt(eq(ApprovalStatus.REJECTED), any());
        verify(agents, never()).findByVerificationStatusOrderByCreatedAt(eq(VerificationStatus.REJECTED), any());
    }

    private static DeliveryAgent agent(VerificationStatus status) {
        DeliveryAgent agent = new DeliveryAgent();
        agent.setId(UUID.randomUUID());
        agent.setVerificationStatus(status);
        return agent;
    }

    @SafeVarargs
    private static <T> Page<T> page(T... items) {
        return new PageImpl<>(List.of(items));
    }
}
