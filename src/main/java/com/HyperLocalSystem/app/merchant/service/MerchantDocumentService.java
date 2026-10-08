package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentsResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UploadPurpose;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UploadRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UploadResponse;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantDocument;
import com.HyperLocalSystem.app.merchant.repository.MerchantDocumentRepository;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Store files: signed upload links (documents private, photos public) and the verification
 * documents ops reviews. Uploads need a token, so they happen after verify-phone.
 */
@Service
@RequiredArgsConstructor
public class MerchantDocumentService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    private final MerchantRepository merchants;
    private final MerchantDocumentRepository documents;
    private final FileStorage fileStorage;

    /**
     * DOCUMENT → merchants/{id}/documents/… (owner and admin only); STORE_PHOTO / ITEM_PHOTO →
     * public/merchants/{id}/… (anyone, for the catalogue). Photos must be images.
     */
    @Transactional(readOnly = true)
    public UploadResponse createUpload(UUID merchantId, UploadRequest request) {
        load(merchantId);
        boolean pdf = request.contentType().equals("application/pdf");
        if (pdf && request.purpose() != UploadPurpose.DOCUMENT) {
            throw ApiException.badRequest("IMAGE_REQUIRED", "Photos must be JPEG or PNG");
        }
        String extension = pdf ? "pdf" : request.contentType().equals("image/png") ? "png" : "jpg";
        String folder = switch (request.purpose()) {
            case DOCUMENT -> "merchants/%s/documents".formatted(merchantId);
            case STORE_PHOTO -> "public/merchants/%s/store".formatted(merchantId);
            case ITEM_PHOTO -> "public/merchants/%s/items".formatted(merchantId);
        };
        String key = "%s/%s.%s".formatted(folder, UUID.randomUUID(), extension);
        FileStorage.UploadTarget target = fileStorage.createUpload(key, request.contentType(), UPLOAD_URL_TTL);
        return new UploadResponse(target.uploadUrl(), key, target.fileUrl(), request.purpose() != UploadPurpose.DOCUMENT,
                target.expiresAt());
    }

    @Transactional(readOnly = true)
    public MerchantDocumentsResponse documents(UUID merchantId) {
        return toResponse(load(merchantId));
    }

    /** Saves (or replaces) one document; locked once the store is approved (mirrors the partner flow). */
    @Transactional
    public MerchantDocumentsResponse upsert(UUID merchantId, MerchantDocument.Type type, MerchantDocumentRequest request) {
        Merchant merchant = load(merchantId);
        if (merchant.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw ApiException.conflict("DOCUMENTS_LOCKED", "Your documents are already verified. Contact support to change them.");
        }
        MerchantDocument document = documents.findByMerchantIdAndType(merchantId, type).orElseGet(MerchantDocument::new);
        document.setMerchantId(merchantId);
        document.setType(type);
        document.setDocumentNumber(request.documentNumber() == null ? null
                : request.documentNumber().replaceAll("\\s", "").toUpperCase(Locale.ROOT));
        document.setFileUrl(request.fileUrl().trim());
        document.setStatus(MerchantDocument.Status.UPLOADED);
        document.setRemarks(null);
        documents.saveAndFlush(document);
        // TODO(edge-case): check fileUrl points at this store's own private upload.
        return toResponse(merchant);
    }

    /** Ops approved the store: every document counts as verified. */
    @Transactional
    public void verifyAll(UUID merchantId) {
        documents.findByMerchantIdOrderByType(merchantId).forEach(document -> {
            document.setStatus(MerchantDocument.Status.VERIFIED);
            document.setRemarks(null);
        });
    }

    public MerchantDocumentsResponse toResponse(Merchant merchant) {
        List<MerchantDocument> saved = documents.findByMerchantIdOrderByType(merchant.getId());
        Set<MerchantDocument.Type> missing = EnumSet.copyOf(MerchantDocument.Type.requiredFor(merchant.getType()));
        saved.stream().filter(d -> d.getStatus() != MerchantDocument.Status.REJECTED).forEach(d -> missing.remove(d.getType()));
        return new MerchantDocumentsResponse(merchant.getApprovalStatus(), missing, saved.stream()
                .map(d -> new MerchantDocumentResponse(d.getType(), mask(d.getDocumentNumber()), d.getFileUrl(), d.getStatus(),
                        d.getRemarks(), d.getUploadedAt()))
                .toList());
    }

    private Merchant load(UUID merchantId) {
        return merchants.findById(merchantId).orElseThrow(() -> ApiException.notFound("Merchant"));
    }

    private static String mask(String number) {
        if (number == null || number.length() <= 4) {
            return number;
        }
        return "X".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }
}
