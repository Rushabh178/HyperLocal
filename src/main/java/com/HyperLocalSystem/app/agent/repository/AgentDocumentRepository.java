package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.AgentDocument;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDocumentRepository extends JpaRepository<AgentDocument, UUID> {

    List<AgentDocument> findByAgentIdOrderByType(UUID agentId);

    Optional<AgentDocument> findByAgentIdAndType(UUID agentId, DocumentType type);
}
