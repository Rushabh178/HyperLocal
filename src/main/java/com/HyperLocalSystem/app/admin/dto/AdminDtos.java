package com.HyperLocalSystem.app.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminProfileResponse(UUID id, String fullName, String email, String phone, Instant createdAt) {
    }

    /** Why an application or document was rejected; shown to the partner / store. */
    public record ReviewRequest(@NotBlank @Size(max = 250) String reason) {
    }
}
