package com.HyperLocalSystem.app.agent.service;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.ActiveRouteResponse;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliverRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliveryHistoryItem;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.EarningsPeriod;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.EarningsSummary;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.KiranaQrScanRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.MarkDeficientRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.PayoutItem;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RejectOfferRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.TaskOffer;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Everything the partner does while on a trip. Every method must first check that the partner is
 * APPROVED and owns the route/stop it is acting on.
 */
@Service
public class AgentTaskService {

    public List<TaskOffer> offers(UUID agentId) {
        throw new NotImplementedException("Task offers");
    }

    /** TODO: first accept wins (row lock on the offer); agent status → ACTIVE; order → PICKING. */
    public ActiveRouteResponse acceptOffer(UUID agentId, UUID offerId) {
        throw new NotImplementedException("Accept offer");
    }

    public void rejectOffer(UUID agentId, UUID offerId, RejectOfferRequest request) {
        throw new NotImplementedException("Reject offer");
    }

    public ActiveRouteResponse currentRoute(UUID agentId) {
        throw new NotImplementedException("Current route");
    }

    public ActiveRouteResponse arrivedAtStop(UUID agentId, UUID stopId) {
        throw new NotImplementedException("Arrived at stop");
    }

    /**
     * Blueprint step 4: the rider scans the Kirana's in-store QR → merchant payment auto-released
     * (PaymentService.captureForMerchant), items go into the COLD chamber, stop COMPLETED.
     */
    public ActiveRouteResponse scanKiranaQr(UUID agentId, UUID stopId, KiranaQrScanRequest request) {
        throw new NotImplementedException("Kirana QR scan");
    }

    /**
     * Point-of-collection discrepancy: void that item's share of the hold, partial refund to the
     * customer's wallet; if nothing is left at this Kirana, strip the stop from the route.
     */
    public ActiveRouteResponse markItemDeficient(UUID agentId, UUID stopId, UUID itemId, MarkDeficientRequest request) {
        throw new NotImplementedException("Mark item deficient");
    }

    /** Restaurant hand-over: hot items into the HOT chamber. */
    public ActiveRouteResponse confirmPickup(UUID agentId, UUID stopId) {
        throw new NotImplementedException("Confirm pickup");
    }

    /** TODO: check the customer's 4-digit OTP; order COMPLETED; capture payment; agent back to AVAILABLE when the route is empty. */
    public ActiveRouteResponse deliver(UUID agentId, UUID orderId, DeliverRequest request) {
        throw new NotImplementedException("Deliver order");
    }

    public void acknowledgeInjection(UUID agentId, UUID injectionId) {
        throw new NotImplementedException("Acknowledge route injection");
    }

    public PageResponse<DeliveryHistoryItem> history(UUID agentId, int page, int size) {
        throw new NotImplementedException("Delivery history");
    }

    public EarningsSummary earnings(UUID agentId, EarningsPeriod period) {
        throw new NotImplementedException("Earnings summary");
    }

    public PageResponse<PayoutItem> payouts(UUID agentId, int page, int size) {
        throw new NotImplementedException("Payouts");
    }
}
