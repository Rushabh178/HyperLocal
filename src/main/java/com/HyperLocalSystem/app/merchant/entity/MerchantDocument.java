package com.HyperLocalSystem.app.merchant.entity;

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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** One verification document per type per store; re-uploading replaces it (mirrors AgentDocument). */
@Entity
@Table(name = "merchant_documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_merchant_document_type", columnNames = {"merchant_id", "doc_type"}))
@Getter
@Setter
@NoArgsConstructor
public class MerchantDocument {

    public enum Type {
        FSSAI_CERT,
        GST_CERT,
        SHOP_PHOTO,
        OWNER_ID,
        BANK_PROOF;

        /** What ops needs before approving a store. GST and bank proof are optional. */
        public static Set<Type> requiredFor(MerchantType merchantType) {
            return merchantType == MerchantType.RESTAURANT
                    ? EnumSet.of(FSSAI_CERT, SHOP_PHOTO, OWNER_ID)
                    : EnumSet.of(SHOP_PHOTO, OWNER_ID);
        }
    }

    public enum Status { UPLOADED, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id")
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private Type type;

    @Column(name = "document_number", length = 30)
    private String documentNumber;

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
