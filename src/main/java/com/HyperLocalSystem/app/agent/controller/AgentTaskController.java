package com.HyperLocalSystem.app.agent.controller;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.ActiveRouteResponse;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliverRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliveryHistoryItem;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.EarningsSummary;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.KiranaQrScanRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.MarkDeficientRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.MarkMissingRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RejectOfferRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.TaskOffer;
import com.HyperLocalSystem.app.agent.service.AgentTaskService;
import com.HyperLocalSystem.app.common.api.EarningsPeriod;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.api.PayoutItem;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService taskService;

    // ---- offers ----

    @GetMapping("/offers")
    public PageResponse<TaskOffer> offers(@AuthenticationPrincipal Jwt jwt) {
        return PageResponse.all(taskService.offers(CurrentUser.id(jwt)));
    }

    @PostMapping("/offers/{offerId}/accept")
    public ActiveRouteResponse acceptOffer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId) {
        return taskService.acceptOffer(CurrentUser.id(jwt), offerId);
    }

    @PostMapping("/offers/{offerId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectOffer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId,
                            @Valid @RequestBody(required = false) RejectOfferRequest request) {
        taskService.rejectOffer(CurrentUser.id(jwt), offerId, request);
    }

    // ---- active route ----

    @GetMapping("/route")
    public ActiveRouteResponse currentRoute(@AuthenticationPrincipal Jwt jwt) {
        return taskService.currentRoute(CurrentUser.id(jwt));
    }

    @PostMapping("/route/stops/{stopId}/arrived")
    public ActiveRouteResponse arrived(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId) {
        return taskService.arrivedAtStop(CurrentUser.id(jwt), stopId);
    }

    /** Pickup step 3 at every store: scanning the store's QR completes the stop. */
    @PostMapping({"/route/stops/{stopId}/scan-qr", "/route/stops/{stopId}/kirana-qr"})
    public ActiveRouteResponse scanQr(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                      @Valid @RequestBody KiranaQrScanRequest request) {
        return taskService.scanQr(CurrentUser.id(jwt), stopId, request);
    }

    /** Pickup step 2 (Kirana, optional): mark missing items. 409 once the stop is done. */
    @PostMapping("/route/stops/{stopId}/deficient")
    public ActiveRouteResponse markMissing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                           @Valid @RequestBody MarkMissingRequest request) {
        return taskService.markMissing(CurrentUser.id(jwt), stopId, request.itemIds());
    }

    @PostMapping("/route/stops/{stopId}/items/{itemId}/deficient")
    public ActiveRouteResponse markDeficient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                             @PathVariable UUID itemId,
                                             @Valid @RequestBody(required = false) MarkDeficientRequest request) {
        return taskService.markMissing(CurrentUser.id(jwt), stopId, List.of(itemId));
    }

    /** Alias of scan-qr kept for older apps; it needs the same {qrPayload} body (400 QR_REQUIRED without it). */
    @PostMapping("/route/stops/{stopId}/picked-up")
    public ActiveRouteResponse pickedUp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                        @RequestBody(required = false) KiranaQrScanRequest request) {
        return taskService.scanQr(CurrentUser.id(jwt), stopId, request);
    }

    @PostMapping("/orders/{orderId}/deliver")
    public ActiveRouteResponse deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                       @Valid @RequestBody DeliverRequest request) {
        return taskService.deliver(CurrentUser.id(jwt), orderId, request);
    }

    @PostMapping("/route/injections/{injectionId}/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledgeInjection(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID injectionId) {
        taskService.acknowledgeInjection(CurrentUser.id(jwt), injectionId);
    }

    // ---- history & earnings ----

    @GetMapping("/deliveries")
    public PageResponse<DeliveryHistoryItem> history(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return taskService.history(CurrentUser.id(jwt), page, size);
    }

    /** Totals and trip count for the period, today's online minutes, and the payouts (paged). */
    @GetMapping("/earnings")
    public EarningsSummary earnings(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "TODAY") EarningsPeriod period,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return taskService.earnings(CurrentUser.id(jwt), period, page, size);
    }

    @GetMapping("/earnings/payouts")
    public PageResponse<PayoutItem> payouts(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return taskService.payouts(CurrentUser.id(jwt), page, size);
    }
}
