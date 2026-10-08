package com.HyperLocalSystem.app.notification.repository;

import com.HyperLocalSystem.app.notification.entity.DeviceToken;
import com.HyperLocalSystem.app.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdAndRole(UUID userId, Role role);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.role = :role")
    int deleteByTokenAndRole(@Param("token") String token, @Param("role") Role role);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token")
    int deleteByToken(@Param("token") String token);
}
