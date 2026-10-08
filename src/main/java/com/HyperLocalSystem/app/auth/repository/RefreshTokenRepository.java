package com.HyperLocalSystem.app.auth.repository;

import com.HyperLocalSystem.app.auth.entity.RefreshToken;
import com.HyperLocalSystem.app.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.role = :role and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("role") Role role, @Param("now") Instant now);
}
