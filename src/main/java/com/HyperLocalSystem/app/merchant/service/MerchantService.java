package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.common.exception.NotImplementedException;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    public MerchantProfileResponse profile(UUID merchantId) {
        throw new NotImplementedException("Merchant profile");
    }

    public MerchantProfileResponse setOpen(UUID merchantId, boolean open) {
        throw new NotImplementedException("Store open/close");
    }

    // ---- Kirana ----

    public List<KiranaBroadcast> openBroadcasts(UUID merchantId) {
        throw new NotImplementedException("Kirana broadcasts");
    }

    /** Delegates to KiranaBroadcastService.claim: first tap wins, then the UPI hold is triggered. */
    public ClaimResponse claimBroadcast(UUID merchantId, UUID broadcastId) {
        throw new NotImplementedException("Claim broadcast (Items in Hand)");
    }

    public StoreQrResponse storeQr(UUID merchantId) {
        throw new NotImplementedException("Store QR");
    }

    // ---- orders (both types) ----

    public List<MerchantOrder> orders(UUID merchantId, MerchantOrderStatus status) {
        throw new NotImplementedException("Merchant orders");
    }

    /** Restaurant accepts and starts the prep timer the rider's arrival is synced to. */
    public MerchantOrder accept(UUID merchantId, UUID orderId, AcceptOrderRequest request) {
        throw new NotImplementedException("Accept order");
    }

    /** Delegates to DispatchOrchestrator.onKitchenDelay. */
    public MerchantOrder reportDelay(UUID merchantId, UUID orderId, PrepDelayRequest request) {
        throw new NotImplementedException("Report prep delay");
    }

    public MerchantOrder markReady(UUID merchantId, UUID orderId) {
        throw new NotImplementedException("Mark order ready");
    }

    // ---- menu / items ----

    public List<CatalogItemResponse> items(UUID merchantId) {
        throw new NotImplementedException("List items");
    }

    public CatalogItemResponse createItem(UUID merchantId, CatalogItemRequest request) {
        throw new NotImplementedException("Create item");
    }

    public CatalogItemResponse updateItem(UUID merchantId, UUID itemId, CatalogItemRequest request) {
        throw new NotImplementedException("Update item");
    }

    public CatalogItemResponse setAvailability(UUID merchantId, UUID itemId, boolean available) {
        throw new NotImplementedException("Item availability");
    }
}
