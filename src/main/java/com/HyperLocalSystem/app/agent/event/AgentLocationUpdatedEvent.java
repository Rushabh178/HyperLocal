package com.HyperLocalSystem.app.agent.event;

import java.util.UUID;

/** A rider on a trip moved: push the new position and ETA to the customers they are carrying for. */
public record AgentLocationUpdatedEvent(UUID agentId) {
}
