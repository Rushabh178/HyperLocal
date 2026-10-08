package com.HyperLocalSystem.app.auth.repository;

import com.HyperLocalSystem.app.auth.entity.OtpCode;
import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByPhoneAndRoleAndPurposeOrderByCreatedAtDesc(String phone, Role role, OtpPurpose purpose);
}
