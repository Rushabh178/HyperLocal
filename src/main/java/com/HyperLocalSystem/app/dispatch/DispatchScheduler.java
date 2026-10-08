package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.dispatch.repository.BroadcastOfferRepository;
import com.HyperLocalSystem.app.dispatch.repository.TaskOfferRepository;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * The dispatch safety net, run on every instance. Each tick:
 * <ol>
 *   <li>opens SCHEDULED broadcast rounds that are due and expands expired OPEN ones;</li>
 *   <li>expires rider offers nobody answered;</li>
 *   <li>dispatches waiting orders (batching first for grocery-only ones, else the next free rider).</li>
 * </ol>
 * Due work is listed without locks, then each row is handled in its own short transaction behind a
 * {@code FOR UPDATE SKIP LOCKED} re-check, so instances never double-process a row and one failing
 * row doesn't block the others.
 */
@Slf4j
@Component
public class DispatchScheduler {

    private static final int BATCH = 50;

    private final BroadcastOfferRepository broadcastOffers;
    private final TaskOfferRepository taskOffers;
    private final UnifiedOrderRepository orders;
    private final KiranaBroadcastService broadcastService;
    private final DispatchOrchestrator dispatch;
    private final TransactionTemplate tx;

    public DispatchScheduler(BroadcastOfferRepository broadcastOffers, TaskOfferRepository taskOffers,
                             UnifiedOrderRepository orders, KiranaBroadcastService broadcastService,
                             DispatchOrchestrator dispatch, PlatformTransactionManager transactionManager) {
        this.broadcastOffers = broadcastOffers;
        this.taskOffers = taskOffers;
        this.orders = orders;
        this.broadcastService = broadcastService;
        this.dispatch = dispatch;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.dispatch.poll-interval-ms:5000}", initialDelayString = "${app.dispatch.poll-interval-ms:5000}")
    public void tick() {
        Instant now = Instant.now();
        for (UUID offerId : broadcastOffers.findDueIds(now, BATCH)) {
            inTransaction("broadcast round " + offerId,
                    () -> broadcastOffers.lockIfDue(offerId, now).ifPresent(broadcastService::advance));
        }
        inTransaction("expire rider offers", () -> taskOffers.expireDue(now));
        for (UUID orderId : orders.findIdsAwaitingRider(BATCH)) {
            inTransaction("dispatch order " + orderId,
                    () -> orders.lockIfAwaitingRider(orderId).ifPresent(dispatch::dispatch));
        }
    }

    private void inTransaction(String what, Runnable work) {
        try {
            tx.executeWithoutResult(status -> work.run());
        } catch (DataIntegrityViolationException e) {
            // Expected race: an event listener on this or another instance did the same work first
            // (e.g. the one-open-offer-per-order index rejected a duplicate offer).
            log.debug("Dispatch tick: {} already handled elsewhere", what);
        } catch (RuntimeException e) {
            log.warn("Dispatch tick: {} failed: {}", what, e.getMessage());
        }
    }
}
