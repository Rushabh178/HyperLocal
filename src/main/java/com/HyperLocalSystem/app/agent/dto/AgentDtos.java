package com.HyperLocalSystem.app.agent.dto;

import com.HyperLocalSystem.app.agent.entity.AgentDocument;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.DutyStatus;
import com.HyperLocalSystem.app.agent.entity.VehicleType;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.auth.dto.AuthDtos;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {
    }

    // ----------------------------------------------------------------- sign up / profile

    /** {@code vehicleNumber} is required for every vehicle type except BICYCLE. */
    public record AgentSignupRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull VehicleType vehicleType,
            @Size(max = 20) String vehicleNumber,
            @NotBlank @Size(max = 60) String city) {
    }

    /** Partial update. Vehicle details are not editable here because they are part of KYC. */
    public record UpdateAgentProfileRequest(
            @Size(min = 1, max = 100) String fullName,
            @Email @Size(max = 150) String email,
            @Size(min = 1, max = 60) String city) {
    }

    public record AgentProfileResponse(
            UUID id,
            String fullName,
            String phone,
            String email,
            boolean phoneVerified,
            VehicleType vehicleType,
            String vehicleNumber,
            String city,
            String thermalBoxId,
            VerificationStatus verificationStatus,
            String rejectionReason,
            Set<DocumentType> missingDocuments,
            DutyStatus dutyStatus,
            Instant createdAt) {
    }

    // ----------------------------------------------------------------- KYC documents

    /** Request a pre-signed URL, PUT the file there from the app, then call PUT /documents/{type}. */
    public record DocumentUploadUrlRequest(@NotBlank @Pattern(regexp = "^(image/(jpeg|png)|application/pdf)$") String contentType) {
    }

    public record DocumentUploadUrlResponse(String uploadUrl, String fileUrl, Instant expiresAt) {
    }

    public record DocumentRequest(
            @Size(max = 30) String documentNumber,
            @NotBlank @Size(max = 500) String fileUrl) {
    }

    public record DocumentResponse(
            DocumentType type,
            String documentNumberMasked,
            String fileUrl,
            AgentDocument.Status status,
            String remarks,
            Instant uploadedAt) {
    }

    public record DocumentsResponse(
            VerificationStatus verificationStatus,
            Set<DocumentType> missingDocuments,
            List<DocumentResponse> documents) {
    }

    // ----------------------------------------------------------------- duty & location

    public record DutyRequest(@NotNull Boolean online) {
    }

    public record DutyResponse(DutyStatus dutyStatus) {
    }

    /** Sent every few seconds while on duty (REST fallback; the WebSocket stream is preferred). */
    public record LocationUpdateRequest(
            @NotNull @Valid GeoPoint location,
            Double headingDegrees,
            Double speedKmph,
            Double accuracyMeters) {
    }
}
