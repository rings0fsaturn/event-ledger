package com.flashsale.ledger.sweep;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.ledger.event.EventType;
import com.flashsale.ledger.event.Events;
import com.flashsale.ledger.repo.EventsRepo;
import com.flashsale.ledger.repo.ReservationRepo;
import com.flashsale.ledger.repo.ReservationResolutionRepo;
import com.flashsale.ledger.repo.StockLevelRepo;
import com.flashsale.ledger.repo.UtilityRepo;
import com.flashsale.ledger.reservation.Reservation;
import com.flashsale.ledger.reservation.ReservationResolution;
import com.flashsale.ledger.reservation.ReservationResolutionOutcome;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

	private final ReservationRepo reservationRepo;
	private final EventsRepo eventsRepo;
	private final StockLevelRepo stockLevelRepo;
	private final ReservationResolutionRepo reservationResolutionRepo;
	private final UtilityRepo utilityRepo;
	private final ObjectMapper objectMapper;
	
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
		String eventKey = reservationId + ":expired";
		try {
			ReservationResolution res = ReservationResolution.builder()
					.reservationId(reservationId)
					.eventKey(eventKey)
					.outcome(ReservationResolutionOutcome.EXPIRED)
					.build();
			reservationResolutionRepo.createExpiredReservationResolution(res);
		}catch(DuplicateKeyException e) {
			return ExpiryOutcome.LOST_RACE;
		}
		
		ReservationExpired expiredReservation = mapReservationToReservationExpired(reservation);
		String expiredReservationAsJson = objectMapper.writeValueAsString(expiredReservation);
		
		Events event = Events.builder()
				.accountId(reservation.accountId())
				.idempotencyKey(eventKey)
				.eventType(EventType.RESERVATION_EXPIRED)
				.payload(expiredReservationAsJson)
				.build();
		eventsRepo.createEvent(event);
		
		int stockCount =  stockLevelRepo.decrementReservedCountInStockLevel(reservation.sku(), reservation.quantity());
		if(stockCount!=1) throw new IllegalStateException("Expiry could not release reserved units: sku=" + reservation.sku());
		
		int rowCount = reservationRepo.setResolvedAtToNowForReservationID(reservationId);
		
		if(rowCount!=1) throw new IllegalStateException("Couldnt update resolvedAt timestamp: "+reservationId);
		
		return ExpiryOutcome.EXPIRED;
		
	}

	private ReservationExpired mapReservationToReservationExpired(Reservation res) {
		return ReservationExpired.builder()
				.accountId(res.accountId())
				.orderId(res.orderId())
				.reservationId(res.reservationId())
				.sku(res.sku())
				.quantity(res.quantity())
				.expiredAt(utilityRepo.getDBClockTime())
				.build();
	}
	
}
