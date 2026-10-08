package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.AgentDutySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDutySessionRepository extends JpaRepository<AgentDutySession, UUID> {

    Optional<AgentDutySession> findFirstByAgentIdAndEndedAtIsNull(UUID agentId);

    /** Sessions that overlap [from, now): still open, or ended after {@code from}. */
    @Query("select s from AgentDutySession s where s.agentId = :agentId and (s.endedAt is null or s.endedAt > :from)")
    List<AgentDutySession> findOverlapping(@Param("agentId") UUID agentId, @Param("from") Instant from);
}
