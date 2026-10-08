package com.HyperLocalSystem.app.merchant.controller;

import com.HyperLocalSystem.app.auth.dto.AuthDtos.AuthResponse;
import com.HyperLocalSystem.app.auth.dto.AuthDtos.ChangePasswordRequest;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.AcceptOrderRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.AvailabilityRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.ClaimResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.KiranaBroadcast;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrder;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrderStatus;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.PrepDelayRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.StoreQrResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.StoreStatusRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantDocumentsResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UpdateMerchantProfileRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UploadRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.UploadResponse;
import com.HyperLocalSystem.app.merchant.entity.MerchantDocument;
import com.HyperLocalSystem.app.merchant.service.MerchantAuthService;
import com.HyperLocalSystem.app.merchant.service.MerchantDocumentService;
import com.HyperLocalSystem.app.merchant.service.MerchantService;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantAuthService authService;
    private final MerchantDocumentService documentService;

    @GetMapping("/me")
    public MerchantProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.profile(CurrentUser.id(jwt));
    }

    /** Partial update: storeName, ownerName, imageUrl, openingHours, cuisines, upiVpa. */
    @PatchMapping("/me")
    public MerchantProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody UpdateMerchantProfileRequest request) {
        return merchantService.updateProfile(CurrentUser.id(jwt), request);
    }

    /** Signed upload link for a document (private) or a store / item photo (public). */
    @PostMapping("/uploads")
    public UploadResponse upload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        return documentService.createUpload(CurrentUser.id(jwt), request);
    }

    @GetMapping("/me/documents")
    public MerchantDocumentsResponse documents(@AuthenticationPrincipal Jwt jwt) {
        return documentService.documents(CurrentUser.id(jwt));
    }

    @PutMapping("/me/documents/{type}")
    public MerchantDocumentsResponse saveDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable MerchantDocument.Type type,
                                                  @Valid @RequestBody MerchantDocumentRequest request) {
        return documentService.upsert(CurrentUser.id(jwt), type, request);
    }

    /** Signs out all other devices; the response carries a fresh session for this one. */
    @PutMapping("/me/password")
    public AuthResponse<MerchantProfileResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(CurrentUser.id(jwt), request);
    }

    @PutMapping("/me/store-status")
    public MerchantProfileResponse setOpen(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StoreStatusRequest request) {
        return merchantService.setOpen(CurrentUser.id(jwt), request.open());
    }

    // ---- Kirana: broadcast + QR ----

    @GetMapping("/broadcasts")
    public List<KiranaBroadcast> broadcasts(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.openBroadcasts(CurrentUser.id(jwt));
    }

    /** The single "Items in Hand" button. */
    @PostMapping("/broadcasts/{broadcastId}/claim")
    public ClaimResponse claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID broadcastId) {
        return merchantService.claimBroadcast(CurrentUser.id(jwt), broadcastId);
    }

    @GetMapping("/store-qr")
    public StoreQrResponse storeQr(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.storeQr(CurrentUser.id(jwt));
    }

    // ---- orders ----

    @GetMapping("/orders")
    public PageResponse<MerchantOrder> orders(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) MerchantOrderStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return merchantService.orders(CurrentUser.id(jwt), status, page, size);
    }

    @PostMapping("/orders/{orderId}/accept")
    public MerchantOrder accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                @Valid @RequestBody AcceptOrderRequest request) {
        return merchantService.accept(CurrentUser.id(jwt), orderId, request);
    }

    @PostMapping("/orders/{orderId}/delay")
    public MerchantOrder reportDelay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                     @Valid @RequestBody PrepDelayRequest request) {
        return merchantService.reportDelay(CurrentUser.id(jwt), orderId, request);
    }

    @PostMapping("/orders/{orderId}/ready")
    public MerchantOrder ready(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return merchantService.markReady(CurrentUser.id(jwt), orderId);
    }

    // ---- menu / items ----

    @GetMapping("/items")
    public PageResponse<CatalogItemResponse> items(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return merchantService.items(CurrentUser.id(jwt), page, size);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CatalogItemRequest request) {
        return merchantService.createItem(CurrentUser.id(jwt), request);
    }

    @PutMapping("/items/{itemId}")
    public CatalogItemResponse updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
                                          @Valid @RequestBody CatalogItemRequest request) {
        return merchantService.updateItem(CurrentUser.id(jwt), itemId, request);
    }

    @PatchMapping("/items/{itemId}/availability")
    public CatalogItemResponse setAvailability(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
                                               @Valid @RequestBody AvailabilityRequest request) {
        return merchantService.setAvailability(CurrentUser.id(jwt), itemId, request.available());
    }
}
