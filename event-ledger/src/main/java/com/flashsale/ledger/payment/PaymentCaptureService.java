package com.flashsale.ledger.payment;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import com.flashsale.ledger.event.EventKey;
import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.event.EventsPayload;
import com.flashsale.ledger.repo.EventsRepo;
import com.flashsale.ledger.repo.RefundIntentRepo;
import com.flashsale.ledger.repo.ReservationRepo;
import com.flashsale.ledger.repo.ReservationResolutionRepo;
import com.flashsale.ledger.repo.StockLevelRepo;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.ReservationResolution;
import com.flashsale.ledger.reservation.ReservationResolutionOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SIMULATION - replaced by the verified-Stripe webhook path in Phase 4.
 *
 * <p>Resolves a reservation on payment and races correctly with
 * {@code ReservationExpiryService}. Gate first, counters next, event next,
 * stamp last. Non-transactional so the primary key decides the winner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCaptureService {

	private static final String PAYMENT_ARRIVED_AFTER_EXPIRY = "payment_arrived_after_expiry";
	private final ReservationRepo reservationRepo;
	private final RefundIntentRepo refundIntentRepo;
	private final ReservationResolutionRepo reservationResolutionRepo;
	private final StockLevelRepo stockLevelRepo;
	private final EventsPayload eventsPayload;
	private final EventsRepo eventsRepo;
	
	public PaymentCaptureOutcome capture(String reservationId, String orderId) {
		
		final Reservation reservation;
		Optional<Reservation> resO = reservationRepo.getUnresolvedReservationById(reservationId);
		if(resO.isEmpty()) {
			return checkReservationResolutionAndDecidePaymentCaptureOutcome(reservationId, orderId);			
		}
		reservation = resO.get();
		String eventKey = reservationId + EventKey.CAPTURED.getValue();
		ReservationResolution capturedResolution = ReservationResolution.builder()
				.reservationId(reservationId)
				.outcome(ReservationResolutionOutcome.CAPTURED)
				.eventKey(eventKey)
				.build();
		
		try {
			reservationResolutionRepo.createReservationResolution(capturedResolution);
		}catch(DuplicateKeyException ex) {
			return checkReservationResolutionAndDecidePaymentCaptureOutcome(reservationId, orderId);	
		}
		
//		Update stock level reserved reduce sold increase
		int stockCount = stockLevelRepo.decrementReservedIncrementSoldInStockLevelByQuantity(reservation.sku(), reservation.quantity());
		if(stockCount !=1) {
			reservationResolutionRepo.clearReservationResolutionById(reservationId);
			throw new IllegalStateException("Payment Captured, Stock not moved for ReservationId:"+reservationId);
		}
			
//		Create an event
		Events event = Events.builder()
				.accountId(reservation.accountId())
				.idempotencyKey(eventKey)
				.eventType(EventType.PAYMENT_CAPTURED)
				.payload(eventsPayload.captured(reservation,null))
				.build();
		eventsRepo.createEvent(event);
		
		int resolveReservationRow = reservationRepo.setResolvedAtToNowForReservationID(reservationId);
		if(resolveReservationRow != 1) throw new IllegalStateException("Captured, Stock Level updated, Unable to stamp ResolvedAt in reservations table");
		return PaymentCaptureOutcome.CAPTURED;
	}

	private PaymentCaptureOutcome checkReservationResolutionAndDecidePaymentCaptureOutcome(String reservationId,
			String orderId) {
		ReservationResolutionOutcome resolutionOutcome;
		try {
			resolutionOutcome = reservationResolutionRepo.getOutcomeFromReservationResolutionById(reservationId);
			log.info("ResolutionOutcome is {} for reservationId:{}",resolutionOutcome.getValue(), reservationId);

			if(resolutionOutcome == ReservationResolutionOutcome.EXPIRED) {
				openRefundIntent(reservationId, orderId);
				return PaymentCaptureOutcome.REFUND_OPENED;
			}
			
		}catch(EmptyResultDataAccessException e) {
			log.warn("No ResolutionOutcome present for reservationId:{}",reservationId);
		}
		return PaymentCaptureOutcome.NOT_CAPTURABLE;
	}

	private void openRefundIntent(String reservationId, String orderId) {
		RefundIntent refund = RefundIntent.builder()
				.reservationId(reservationId)
				.orderId(orderId)
				.reason(PAYMENT_ARRIVED_AFTER_EXPIRY)
				.build();
		refundIntentRepo.createRefundIntent(refund);
		
	}
}
