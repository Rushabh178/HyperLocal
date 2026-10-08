package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.RouteInjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RouteInjectionRepository extends JpaRepository<RouteInjection, UUID> {

    Optional<RouteInjection> findByIdAndAgentId(UUID id, UUID agentId);
}
