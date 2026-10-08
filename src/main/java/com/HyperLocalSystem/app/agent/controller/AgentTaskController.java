package com.HyperLocalSystem.app.agent.controller;

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
import com.HyperLocalSystem.app.agent.service.AgentTaskService;
import com.HyperLocalSystem.app.common.api.PageResponse;
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
    public List<TaskOffer> offers(@AuthenticationPrincipal Jwt jwt) {
        return taskService.offers(CurrentUser.id(jwt));
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

    @PostMapping("/route/stops/{stopId}/kirana-qr")
    public ActiveRouteResponse scanKiranaQr(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                            @Valid @RequestBody KiranaQrScanRequest request) {
        return taskService.scanKiranaQr(CurrentUser.id(jwt), stopId, request);
    }

    @PostMapping("/route/stops/{stopId}/items/{itemId}/deficient")
    public ActiveRouteResponse markDeficient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId,
                                             @PathVariable UUID itemId,
                                             @Valid @RequestBody(required = false) MarkDeficientRequest request) {
        return taskService.markItemDeficient(CurrentUser.id(jwt), stopId, itemId, request);
    }

    @PostMapping("/route/stops/{stopId}/picked-up")
    public ActiveRouteResponse pickedUp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stopId) {
        return taskService.confirmPickup(CurrentUser.id(jwt), stopId);
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

    @GetMapping("/earnings")
    public EarningsSummary earnings(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "TODAY") EarningsPeriod period) {
        return taskService.earnings(CurrentUser.id(jwt), period);
    }

    @GetMapping("/earnings/payouts")
    public PageResponse<PayoutItem> payouts(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return taskService.payouts(CurrentUser.id(jwt), page, size);
    }
}
