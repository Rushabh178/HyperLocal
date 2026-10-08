package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.catalog.repository.CatalogItemRepository;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.AcceptOrderRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.ClaimResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.KiranaBroadcast;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrder;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrderStatus;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.PrepDelayRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.StoreQrResponse;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchants;
    private final CatalogItemRepository catalogItems;
    private final StoreQrService storeQrService;

    // ----------------------------------------------------------------- profile

    @Transactional(readOnly = true)
    public MerchantProfileResponse profile(UUID merchantId) {
        return toResponse(load(merchantId));
    }

    /** Only approved stores can open; closing is always allowed. */
    @Transactional
    public MerchantProfileResponse setOpen(UUID merchantId, boolean open) {
        Merchant merchant = load(merchantId);
        if (open && merchant.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.forbidden("MERCHANT_NOT_APPROVED", "Your store must be approved before it can open");
        }
        merchant.setOpen(open);
        return toResponse(merchant);
    }

    // ---- Kirana ----

    public List<KiranaBroadcast> openBroadcasts(UUID merchantId) {
        throw new NotImplementedException("Kirana broadcasts");
    }

    /** Delegates to KiranaBroadcastService.claim: first tap wins. */
    public ClaimResponse claimBroadcast(UUID merchantId, UUID broadcastId) {
        throw new NotImplementedException("Claim broadcast (Items in Hand)");
    }

    /** Short-lived signed QR shown on the shop's phone; the rider scans it at pickup. */
    @Transactional(readOnly = true)
    public StoreQrResponse storeQr(UUID merchantId) {
        Merchant merchant = load(merchantId);
        if (merchant.getType() != MerchantType.KIRANA) {
            throw ApiException.forbidden("KIRANA_ONLY", "Only Kirana stores have a pickup QR");
        }
        StoreQrService.SignedQr qr = storeQrService.issue(merchantId);
        return new StoreQrResponse(qr.payload(), qr.expiresAt());
    }

    // ---- orders (both types) ----

    public List<MerchantOrder> orders(UUID merchantId, MerchantOrderStatus status) {
        throw new NotImplementedException("Merchant orders");
    }

    /** Restaurant accepts and starts the prep timer the rider's arrival is synced to. */
    public MerchantOrder accept(UUID merchantId, UUID orderId, AcceptOrderRequest request) {
        throw new NotImplementedException("Accept order");
    }

    public MerchantOrder reportDelay(UUID merchantId, UUID orderId, PrepDelayRequest request) {
        throw new NotImplementedException("Report prep delay");
    }

    public MerchantOrder markReady(UUID merchantId, UUID orderId) {
        throw new NotImplementedException("Mark order ready");
    }

    // ---- menu / items ----

    @Transactional(readOnly = true)
    public PageResponse<CatalogItemResponse> items(UUID merchantId, int page, int size) {
        return PageResponse.of(catalogItems.findByMerchantIdOrderByCategoryAscNameAsc(merchantId,
                PageResponse.request(page, size)), MerchantService::toItemResponse);
    }

    /** The item type follows the store: restaurants list FOOD, Kiranas list GROCERY. */
    @Transactional
    public CatalogItemResponse createItem(UUID merchantId, CatalogItemRequest request) {
        Merchant merchant = load(merchantId);
        CatalogItem item = new CatalogItem();
        item.setMerchantId(merchantId);
        item.setType(merchant.getType() == MerchantType.RESTAURANT ? ItemType.FOOD : ItemType.GROCERY);
        apply(item, request);
        return toItemResponse(catalogItems.save(item));
    }

    @Transactional
    public CatalogItemResponse updateItem(UUID merchantId, UUID itemId, CatalogItemRequest request) {
        CatalogItem item = loadItem(merchantId, itemId);
        apply(item, request);
        return toItemResponse(item);
    }

    @Transactional
    public CatalogItemResponse setAvailability(UUID merchantId, UUID itemId, boolean available) {
        CatalogItem item = loadItem(merchantId, itemId);
        item.setAvailable(available);
        return toItemResponse(item);
    }

    private static void apply(CatalogItem item, CatalogItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(trimToNull(request.description()));
        item.setPrice(request.price());
        item.setCategory(request.category().trim());
        item.setVeg(request.veg());
        item.setZone(request.zone());
        item.setImageUrl(trimToNull(request.imageUrl()));
        item.setAvailable(request.available());
        item.setBrand(trimToNull(request.brand()));
        item.setPackSize(trimToNull(request.packSize()));
        item.setMrp(request.mrp());
        // TODO(edge-case): reject price > mrp, and FOOD items with grocery-only fields.
    }

    // ----------------------------------------------------------------- helpers

    private CatalogItem loadItem(UUID merchantId, UUID itemId) {
        return catalogItems.findByIdAndMerchantId(itemId, merchantId)
                .orElseThrow(() -> ApiException.notFound("Item"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    Merchant load(UUID merchantId) {
        return merchants.findById(merchantId)
                .filter(merchant -> merchant.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Merchant"));
    }

    public static MerchantProfileResponse toResponse(Merchant merchant) {
        return new MerchantProfileResponse(merchant.getId(), merchant.getType(), merchant.getStoreName(),
                merchant.getOwnerName(), merchant.getPhone(), merchant.getAddress(),
                GeoUtils.fromPoint(merchant.getLocation()), merchant.isOpen(), merchant.getApprovalStatus(),
                merchant.getRejectionReason(), merchant.getCreatedAt());
    }

    public static CatalogItemResponse toItemResponse(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getType(), item.getName(), item.getDescription(),
                item.getPrice(), item.getCategory(), item.getVeg(), item.getZone(), item.getImageUrl(),
                item.isAvailable(), item.getBrand(), item.getPackSize(), item.getMrp());
    }
}
