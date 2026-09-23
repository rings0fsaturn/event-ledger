package com.flashsale.ledger.sweep;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.ledger.event.EventKey;
import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.event.EventsPayload;
import com.flashsale.ledger.repo.EventsRepo;
import com.flashsale.ledger.repo.ReservationRepo;
import com.flashsale.ledger.repo.ReservationResolutionRepo;
import com.flashsale.ledger.repo.StockLevelRepo;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.ReservationResolution;
import com.flashsale.ledger.reservation.ReservationResolutionOutcome;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

	private final ReservationRepo reservationRepo;
	private final EventsRepo eventsRepo;
	private final StockLevelRepo stockLevelRepo;
	private final ReservationResolutionRepo reservationResolutionRepo;
	private final EventsPayload eventsPayload;
	
	/**
	 * 1. getUnresolvedExpiringReservationById
	 * 2. createExpiredReservationResolution
	 * 3. createExpiredEvent
	 * 4. Reduce StockLevel by Quantity
	 * 5. Mark Reservation resolvedTime
	 * 
	 * 
	 */
	
	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
	public ExpiryOutcome expireOne(String reservationId) {
		Optional<Reservation> reservationO = reservationRepo.getUnresolvedExpiringReservationById(reservationId);
		if(reservationO.isEmpty())
			return ExpiryOutcome.ALREADY_RESOLVED;
		
		Reservation reservation = reservationO.get();
		String eventKey = reservationId +EventKey.EXPIRED.getValue();
		try {
			ReservationResolution res = ReservationResolution.builder()
					.reservationId(reservationId)
					.eventKey(eventKey)
					.outcome(ReservationResolutionOutcome.EXPIRED)
					.build();
			reservationResolutionRepo.createReservationResolution(res);
		}catch(DuplicateKeyException e) {
			return ExpiryOutcome.LOST_RACE;
		}
		
		Events event = Events.builder()
				.accountId(reservation.accountId())
				.idempotencyKey(eventKey)
				.eventType(EventType.RESERVATION_EXPIRED)
				.payload(eventsPayload.expired(reservation, null))
				.build();
		eventsRepo.createEvent(event);
		
		int stockCount =  stockLevelRepo.decrementReservedCountInStockLevel(reservation.sku(), reservation.quantity());
		if(stockCount!=1) throw new IllegalStateException("Expiry could not release reserved units: sku=" + reservation.sku());
		
		int rowCount = reservationRepo.setResolvedAtToNowForReservationID(reservationId);
		
		if(rowCount!=1) throw new IllegalStateException("Couldnt update resolvedAt timestamp: "+reservationId);
		
		return ExpiryOutcome.EXPIRED;
		
	}
	
}
