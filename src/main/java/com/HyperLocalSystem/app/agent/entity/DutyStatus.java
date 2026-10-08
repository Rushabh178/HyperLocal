package com.HyperLocalSystem.app.agent.entity;

/** Values of the existing {@code delivery_agents.status} column. */
public enum DutyStatus {
    /** Online and free to take an order. */
    AVAILABLE,
    /** Online and carrying at least one order (a batching-injection candidate). */
    ACTIVE,
    OFFLINE
}
