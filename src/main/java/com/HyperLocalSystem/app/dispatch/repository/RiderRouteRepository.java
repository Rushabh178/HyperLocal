package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiderRouteRepository extends JpaRepository<RiderRoute, UUID> {

    Optional<RiderRoute> findByAgentIdAndStatus(UUID agentId, RiderRoute.Status status);
}
