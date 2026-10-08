package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CancelOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderDetail;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderSummary;
import com.HyperLocalSystem.app.order.dto.OrderDtos.RateOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TimelineResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomerOrderService {

    /**
     * TODO (blueprint step 1 "Consolidated Cart"):
     * <ol>
     *   <li>Validate cart + address (2 km radius), price it, honour the Idempotency-Key.</li>
     *   <li>Insert unified_orders (status PENDING), clear the cart.</li>
     *   <li>Create the UPI hold (PaymentService). When the hold is confirmed, the webhook calls
     *       DispatchOrchestrator.onPaymentConfirmed(orderId) for parallel dispatch.</li>
     * </ol>
     */
    public CheckoutResponse checkout(UUID customerId, CheckoutRequest request, String idempotencyKey) {
        throw new NotImplementedException("Checkout");
    }

    public PageResponse<OrderSummary> list(UUID customerId, OrderStatus status, int page, int size) {
        throw new NotImplementedException("Order history");
    }

    public OrderDetail get(UUID customerId, UUID orderId) {
        throw new NotImplementedException("Order details");
    }

    /** TODO: build the single-line timeline (Kirana → Restaurant → Home) from the rider's route stops. */
    public TimelineResponse timeline(UUID customerId, UUID orderId) {
        throw new NotImplementedException("Unified order timeline");
    }

    public TrackingResponse tracking(UUID customerId, UUID orderId) {
        throw new NotImplementedException("Order tracking snapshot");
    }

    /** TODO: allowed only before pickup; release the UPI hold, cancel the kitchen ticket and Kirana claim. */
    public OrderDetail cancel(UUID customerId, UUID orderId, CancelOrderRequest request) {
        throw new NotImplementedException("Cancel order");
    }

    public void rate(UUID customerId, UUID orderId, RateOrderRequest request) {
        throw new NotImplementedException("Rate order");
    }
}
