package com.HyperLocalSystem.app.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One KYC document per type per partner; re-uploading replaces it. */
@Entity
@Table(name = "agent_documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_agent_document_type", columnNames = {"agent_id", "doc_type"}))
@Getter
@Setter
@NoArgsConstructor
public class AgentDocument {

    public enum Status { UPLOADED, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id")
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType type;

    @Column(name = "document_number", length = 30)
    private String documentNumber;

    /** Location in object storage (S3/GCS). The upload itself goes through a pre-signed URL. */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    @Column(length = 250)
    private String remarks;

    @UpdateTimestamp
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
