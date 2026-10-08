package com.HyperLocalSystem.app.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One stretch of being online (going online opens it, going offline closes it); feeds onlineMinutes. */
@Entity
@Table(name = "agent_duty_sessions", indexes = @Index(name = "idx_agent_duty_sessions_agent", columnList = "agent_id, started_at"))
@Getter
@Setter
@NoArgsConstructor
public class AgentDutySession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Null while still online. */
    @Column(name = "ended_at")
    private Instant endedAt;

    public AgentDutySession(UUID agentId, Instant startedAt) {
        this.agentId = agentId;
        this.startedAt = startedAt;
    }
}
