package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Blueprint §3 "Parallel Dispatch Sequence". Internal service, no HTTP endpoints.
 */
@Service
public class DispatchOrchestrator {

    /**
     * Called by the payment webhook once the UPI hold succeeds.
     * <ol>
     *   <li>Food part: send the kitchen ticket to the restaurant (prep window, e.g. 15-20 min).</li>
     *   <li>Grocery part, in parallel: KiranaBroadcastService.broadcast(...) to the 5 closest shops
     *       within 500 m of the restaurant / rider.</li>
     *   <li>Solo grocery order: first try CoLinearBatchingEngine.tryInject(orderId); only if no
     *       active rider qualifies, offer it to the nearest AVAILABLE rider.</li>
     *   <li>Pick the rider so they reach the Kirana first and the restaurant as the prep timer ends.</li>
     * </ol>
     */
    public void onPaymentConfirmed(UUID orderId) {
        throw new NotImplementedException("Parallel dispatch");
    }

    /**
     * Edge case "Kitchen Bottlenecks": the restaurant reported a delay. Hold back (re-schedule) the
     * Kirana broadcast / rider's Kirana stop by {@code delay} so cold items don't sit at an ambient
     * kitchen counter.
     */
    public void onKitchenDelay(UUID orderId, Duration delay) {
        throw new NotImplementedException("Kitchen delay handling");
    }
}
