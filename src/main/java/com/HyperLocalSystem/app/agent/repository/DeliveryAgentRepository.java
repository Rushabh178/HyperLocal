package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryAgentRepository extends JpaRepository<DeliveryAgent, UUID> {

    Optional<DeliveryAgent> findByPhone(String phone);

    Optional<DeliveryAgent> findByEmailIgnoreCase(String email);
}
