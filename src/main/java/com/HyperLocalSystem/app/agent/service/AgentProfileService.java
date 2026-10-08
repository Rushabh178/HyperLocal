package com.HyperLocalSystem.app.agent.service;

import com.HyperLocalSystem.app.agent.dto.AgentDtos.AgentProfileResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentUploadUrlRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentUploadUrlResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DocumentsResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.DutyResponse;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.LocationUpdateRequest;
import com.HyperLocalSystem.app.agent.dto.AgentDtos.UpdateAgentProfileRequest;
import com.HyperLocalSystem.app.agent.entity.AgentDocument;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.DocumentType;
import com.HyperLocalSystem.app.agent.entity.DutyStatus;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.AgentDocumentRepository;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.agent.event.AgentLocationUpdatedEvent;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import com.HyperLocalSystem.app.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentProfileService {

    private final DeliveryAgentRepository agents;
    private final AgentDocumentRepository documents;
    private final SpatialIndexService spatialIndex;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher events;

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    // ----------------------------------------------------------------- profile

    @Transactional(readOnly = true)
    public AgentProfileResponse get(UUID agentId) {
        return toResponse(load(agentId));
    }

    @Transactional
    public AgentProfileResponse update(UUID agentId, UpdateAgentProfileRequest request) {
        DeliveryAgent agent = load(agentId);
        if (request.fullName() != null) {
            agent.setName(request.fullName().trim());
        }
        if (request.city() != null) {
            agent.setCity(request.city().trim());
        }
        if (request.email() != null) {
            String email = request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
            if (email != null) {
                agents.findByEmailIgnoreCase(email)
                        .filter(other -> !other.getId().equals(agentId))
                        .ifPresent(other -> {
                            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already linked to another account");
                        });
            }
            agent.setEmail(email);
        }
        return toResponse(agent);
    }

    // ----------------------------------------------------------------- KYC documents

    @Transactional(readOnly = true)
    public DocumentsResponse listDocuments(UUID agentId) {
        return documentsResponse(load(agentId));
    }

    /**
     * Saves (or replaces) one KYC document. When every required document is present the
     * application moves to UNDER_REVIEW for an admin to approve.
     */
    @Transactional
    public DocumentsResponse upsertDocument(UUID agentId, DocumentType type, DocumentRequest request) {
        DeliveryAgent agent = load(agentId);
        if (agent.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw ApiException.conflict("DOCUMENTS_LOCKED", "Your documents are already verified. Contact support to change them.");
        }
        AgentDocument document = documents.findByAgentIdAndType(agentId, type).orElseGet(AgentDocument::new);
        document.setAgentId(agentId);
        document.setType(type);
        document.setDocumentNumber(request.documentNumber() == null ? null
                : request.documentNumber().replaceAll("\\s", "").toUpperCase(Locale.ROOT));
        document.setFileUrl(request.fileUrl().trim());
        document.setStatus(AgentDocument.Status.UPLOADED);
        document.setRemarks(null);
        documents.saveAndFlush(document);

        boolean awaitingDocuments = agent.getVerificationStatus() == VerificationStatus.PENDING_DOCUMENTS
                || agent.getVerificationStatus() == VerificationStatus.REJECTED;
        if (awaitingDocuments && missingDocuments(agent).isEmpty()) {
            agent.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
            agent.setRejectionReason(null);
        }
        return documentsResponse(agent);
    }

    /**
     * A short-lived upload link scoped to agents/{agentId}/{type}/{uuid}. The app PUTs the file there,
     * then saves the returned {@code fileUrl} with PUT /documents/{type}.
     */
    @Transactional(readOnly = true)
    public DocumentUploadUrlResponse createDocumentUploadUrl(UUID agentId, DocumentType type, DocumentUploadUrlRequest request) {
        DeliveryAgent agent = load(agentId);
        if (agent.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw ApiException.conflict("DOCUMENTS_LOCKED", "Your documents are already verified. Contact support to change them.");
        }
        String extension = switch (request.contentType()) {
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            default -> "jpg";
        };
        String key = "agents/%s/%s/%s.%s".formatted(agentId, type.name().toLowerCase(Locale.ROOT), UUID.randomUUID(), extension);
        FileStorage.UploadTarget target = fileStorage.createUpload(key, request.contentType(), UPLOAD_URL_TTL);
        return new DocumentUploadUrlResponse(target.uploadUrl(), target.fileUrl(), target.expiresAt());
    }

    // ----------------------------------------------------------------- duty & location

    @Transactional
    public DutyResponse setDuty(UUID agentId, boolean online) {
        DeliveryAgent agent = load(agentId);
        if (online) {
            if (agent.getVerificationStatus() != VerificationStatus.APPROVED) {
                throw ApiException.forbidden("AGENT_NOT_APPROVED", "Your profile must be approved before you can go online");
            }
            if (agent.getDutyStatus() == DutyStatus.OFFLINE || agent.getDutyStatus() == null) {
                agent.setDutyStatus(DutyStatus.AVAILABLE);
            }
        } else {
            if (agent.getDutyStatus() == DutyStatus.ACTIVE) {
                throw ApiException.conflict("DELIVERY_IN_PROGRESS", "Finish your current deliveries before going offline");
            }
            agent.setDutyStatus(DutyStatus.OFFLINE);
        }
        return new DutyResponse(agent.getDutyStatus());
    }

    /**
     * Stores the live position and its H3 cell. Shared by the REST endpoint and the WebSocket stream.
     * While on a trip, the customers being served get the new position and ETA (after commit).
     */
    @Transactional
    public void updateLocation(UUID agentId, LocationUpdateRequest request) {
        DeliveryAgent agent = load(agentId);
        if (agent.getDutyStatus() == DutyStatus.OFFLINE || agent.getDutyStatus() == null) {
            throw ApiException.conflict("AGENT_OFFLINE", "Go online before sharing your location");
        }
        double lat = request.location().lat();
        double lng = request.location().lng();
        agent.setCurrentLocation(GeoUtils.toPoint(lat, lng));
        agent.setH3Index(spatialIndex.cellOf(lat, lng));
        if (agent.getDutyStatus() == DutyStatus.ACTIVE) {
            events.publishEvent(new AgentLocationUpdatedEvent(agentId));
        }
        // TODO(edge-case): throttle pushes / DB writes for very chatty clients (e.g. one per 2 s per rider).
    }

    // ----------------------------------------------------------------- helpers

    DeliveryAgent load(UUID agentId) {
        return agents.findById(agentId)
                .filter(agent -> agent.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Delivery partner"));
    }

    Set<DocumentType> missingDocuments(DeliveryAgent agent) {
        if (agent.getVehicleType() == null) {
            return Set.of();
        }
        Set<DocumentType> missing = EnumSet.copyOf(agent.getVehicleType().requiredDocuments());
        documents.findByAgentIdOrderByType(agent.getId()).stream()
                .filter(document -> document.getStatus() != AgentDocument.Status.REJECTED)
                .forEach(document -> missing.remove(document.getType()));
        return missing;
    }

    public AgentProfileResponse toResponse(DeliveryAgent agent) {
        return new AgentProfileResponse(agent.getId(), agent.getName(), agent.getPhone(), agent.getEmail(),
                agent.isPhoneVerified(), agent.getVehicleType(), agent.getVehicleNumber(), agent.getCity(),
                agent.getThermalBoxId(), agent.getVerificationStatus(), agent.getRejectionReason(),
                missingDocuments(agent), agent.getDutyStatus(), agent.getCreatedAt());
    }

    private DocumentsResponse documentsResponse(DeliveryAgent agent) {
        List<DocumentResponse> items = documents.findByAgentIdOrderByType(agent.getId()).stream()
                .map(d -> new DocumentResponse(d.getType(), mask(d.getDocumentNumber()), d.getFileUrl(),
                        d.getStatus(), d.getRemarks(), d.getUploadedAt()))
                .toList();
        return new DocumentsResponse(agent.getVerificationStatus(), missingDocuments(agent), items);
    }

    /** Never echo full Aadhaar/PAN numbers back to the client. */
    private static String mask(String number) {
        if (number == null || number.length() <= 4) {
            return number;
        }
        return "X".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }
}
