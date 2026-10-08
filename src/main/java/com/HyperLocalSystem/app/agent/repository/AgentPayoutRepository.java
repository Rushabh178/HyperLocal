package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.AgentPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentPayoutRepository extends JpaRepository<AgentPayout, UUID> {

    Page<AgentPayout> findByAgentIdOrderByCreatedAtDesc(UUID agentId, Pageable pageable);
}
